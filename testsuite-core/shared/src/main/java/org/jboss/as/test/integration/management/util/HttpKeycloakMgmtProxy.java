/*
 * JBoss, Home of Professional Open Source.
 * Copyright (C) 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

package org.jboss.as.test.integration.management.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jboss.dmr.ModelNode;

/**
 * Helper class, similar to HttpMgmtProxy, but with knowledge on how to
 * authenticate against a console protected via Keycloak.
 *
 * @author Juraci Paixão Kröhling <juraci at kroehling.de>
 */
public class HttpKeycloakMgmtProxy {

    /*
    TODO: Refactoring tasks
    1) Better error handling
    2) Move to wildfly from wildfly-core
    3) Move the code from the main() into proper tests. This takes longer,
        as we'll probably need to setup how the test will build our custom
        wildfly distribution. Perhaps wait for the decision on "how to bootstrap keycloak/wildfly"?
    */

    private static final String APPLICATION_JSON = "application/json";
    private static final String KEYCLOAK_POST_AUTH_PATH = "/auth/realms/master/tokens/auth/request/login";
    private URL url;
    private final DefaultHttpClient httpClient = new DefaultHttpClient();
    private final HttpContext httpContext = new BasicHttpContext();
    private static final Logger logger = Logger.getLogger(HttpKeycloakMgmtProxy.class.getName());

    public HttpKeycloakMgmtProxy(URL mgmtURL) {
        this(mgmtURL, new UsernamePasswordCredentials("admin", "admin"));
    }

    public HttpKeycloakMgmtProxy(URL mgmtURL, UsernamePasswordCredentials credentials) {
        try {
            this.url = mgmtURL;

            // we use a basic cookie store, so that cookies are persistent across
            // the requests for this specific http client.
            // this way, we don't need to parse/select the cookies we are interested on
            httpClient.setCookieStore(new BasicCookieStore());

            // first of all, let's send a request to the console
            // it will redirect us to a Keycloak-based login page, with a specific
            // state on the query string, that we need
            // otherwise, we could just have sent a post to keycloak and wait for
            // the redirect to the console
            HttpGet httpGet = new HttpGet(this.url.toURI());
            HttpResponse httpResponse = httpClient.execute(httpGet, httpContext);

            // http client follows the redirects from GETs automatically, so, we
            // don't know exactly whats the URL, but we need the querystring from it
            HttpUriRequest httpRedirectedGet = (HttpUriRequest) httpContext.getAttribute(ExecutionContext.HTTP_REQUEST);
            HttpHost httpHost = (HttpHost) httpContext.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
            EntityUtils.consume(httpResponse.getEntity());

            // this returns the url of which we are right now
            // we need to send a post to a similar url (scheme, host, ...), just
            // with another path
            URI redirectedToUri = httpRedirectedGet.getURI();
            URI uriToPost = new URI(
                    httpHost.getSchemeName(),       // http / https
                    redirectedToUri.getUserInfo(),  // empty, as KC doesn't stores info here
                    httpHost.getHostName(),         // funny that redirectedToUri doesn't have this
                    httpHost.getPort(),             // ditto
                    KEYCLOAK_POST_AUTH_PATH,        // the path to post to
                    redirectedToUri.getQuery(),     // the query parameters, including the state (TODO: does KC really requires this?)
                    redirectedToUri.getFragment()   // probably always empty
            );

            // now, we prepare the POST login request and execute it
            HttpPost httpPost = new HttpPost(uriToPost);
            List <NameValuePair> postParams = new ArrayList<>(2);
            postParams.add(new BasicNameValuePair("username", credentials.getUserName()));
            postParams.add(new BasicNameValuePair("password", credentials.getPassword()));
            httpPost.setEntity(new UrlEncodedFormEntity(postParams));
            httpResponse = httpClient.execute(httpPost, httpContext);

            // once keycloak accepts our login/password, it redirects us to the
            // original url, so, we get it from the Location header, as it contains
            // the keycloak's specific query parameters
            String location = httpResponse.getLastHeader("Location").getValue();
            EntityUtils.consume(httpResponse.getEntity()); // release client

            // now, we do a final request, to get the management console's cookies
            // like jsessionid
            httpGet = new HttpGet(location);
            httpResponse = httpClient.execute(httpGet, httpContext);
            EntityUtils.consume(httpResponse.getEntity()); // release client

        } catch (Exception ex) {
            // decide what to do when this fails... right now, it just continues,
            // breaking with a NPE down the road, but a decision needs to be made for this
            // the HttpMgmtProxy throws simple exceptions, we might want to do the same
            logger.log(Level.SEVERE, null, ex);
        }

    }

    public ModelNode sendGetCommand(String cmd) throws Exception {
        return ModelNode.fromJSONString(sendGetCommandJson(cmd));
    }

    public String sendGetCommandJson(String cmd) throws Exception {
        HttpGet get = new HttpGet(url.toURI().toString() + cmd);

        HttpResponse response = httpClient.execute(get, httpContext);
        return EntityUtils.toString(response.getEntity());
    }

    public ModelNode sendPostCommand(String address, String operation) throws Exception {
        return sendPostCommand(getOpNode(address, operation));
    }

    public ModelNode sendPostCommand(ModelNode cmd) throws Exception {

        String cmdStr = cmd.toJSONString(true);
        HttpPost post = new HttpPost(url.toURI());
        StringEntity entity = new StringEntity(cmdStr);
        entity.setContentType(APPLICATION_JSON);
        post.setEntity(entity);

        HttpResponse response = httpClient.execute(post, httpContext);
        String str = EntityUtils.toString(response.getEntity());
        if (response.getStatusLine().getStatusCode()==200){
            return ModelNode.fromJSONString(str);
        }else{
            throw new Exception("Could not execute command: "+str);
        }
    }

    public static ModelNode getOpNode(String address, String operation) {
        ModelNode op = new ModelNode();

        // set address
        String[] pathSegments = address.split("/");
        ModelNode list = op.get("address").setEmptyList();
        for (String segment : pathSegments) {
            String[] elements = segment.split("=");
            list.add(elements[0], elements[1]);
        }
        op.get("operation").set(operation);
        return op;
    }

    public static void main(String[] args) throws MalformedURLException, Exception {
        URL mgmtURL = new URL("http", "localhost", 9990, "/management");
        HttpKeycloakMgmtProxy httpMgmt = new HttpKeycloakMgmtProxy(mgmtURL);

        String cmd = "/subsystem/logging?operation=resource";
        ModelNode node = httpMgmt.sendGetCommand(cmd);

        if (node.has("root-logger")) {
            System.out.println("has root logger");
        } else {
            System.out.println("does NOT have root logger");
        }
    }
}
