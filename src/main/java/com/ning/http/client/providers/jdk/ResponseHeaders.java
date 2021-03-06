/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.ning.http.client.providers.jdk;

import com.ning.http.client.AsyncHttpProvider;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.HttpResponseHeaders;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * A class that represent the HTTP headers.
 */
public class ResponseHeaders extends HttpResponseHeaders {

    private final HttpURLConnection urlConnection;
    private final FluentCaseInsensitiveStringsMap headers;

    public ResponseHeaders(URI uri, HttpURLConnection urlConnection, AsyncHttpProvider<HttpURLConnection> provider) {
        super(uri, provider, false);
        this.urlConnection = urlConnection;
        headers = computerHeaders();
    }

    private FluentCaseInsensitiveStringsMap computerHeaders() {
        FluentCaseInsensitiveStringsMap h = new FluentCaseInsensitiveStringsMap();

        Map<String, List<String>> uh = urlConnection.getHeaderFields();

        for (Map.Entry<String, List<String>> e : uh.entrySet()) {
            if (e.getKey() != null) {
                h.add(e.getKey(), e.getValue());
            }
        }
        return h;
    }

    /**
     * Return the HTTP header
     *
     * @return an {@link com.ning.http.client.FluentCaseInsensitiveStringsMap}
     */
    @Override
    public FluentCaseInsensitiveStringsMap getHeaders() {
        return headers;
    }
}