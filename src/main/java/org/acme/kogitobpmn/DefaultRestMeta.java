/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acme.kogitobpmn;

import java.util.HashMap;
import java.util.Map;

public class DefaultRestMeta {

    public static final String HOST_PRM = "host";
    public static final String PORT_PRM = "port";
    public static final String URL_PRM = "url";
    public static final String METHOD_PRM = "method";
    public static final String USERNAME_PRM = "username";

    private String host;
    private Integer port;
    private String url;
    private String username;
    private String method;
    private String await;

    public String getAwait() {
        return await;
    }

    public void setAwait(String await) {
        this.await = await;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(final String method) {
        this.method = method;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public void setPort(final Integer port) {
        this.port = port;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultRestMeta{");
        sb.append("host='").append(host).append('\'');
        sb.append(", port=").append(port);
        sb.append(", url='").append(url).append('\'');
        sb.append(", username='").append(username).append('\'');
        sb.append(", method='").append(method).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public Map<String, Object> toMap() {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(HOST_PRM, host);
        parameters.put(PORT_PRM, port);
        parameters.put(URL_PRM, url);
        parameters.put(METHOD_PRM, method);
        parameters.put(USERNAME_PRM, username);
        return parameters;
    }
}
