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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.kie.kogito.process.workitems.InternalKogitoWorkItem;
import org.kogito.workitem.rest.auth.ApiKeyAuthDecorator;
import org.kogito.workitem.rest.auth.AuthDecorator;
import org.kogito.workitem.rest.auth.BasicAuthDecorator;
import org.kogito.workitem.rest.auth.BearerTokenAuthDecorator;
import org.kogito.workitem.rest.bodybuilders.DefaultWorkItemHandlerBodyBuilder;
import org.kogito.workitem.rest.bodybuilders.RestWorkItemHandlerBodyBuilder;
import org.kogito.workitem.rest.decorators.ParamsDecorator;
import org.kogito.workitem.rest.decorators.PrefixParamsDecorator;
import org.kogito.workitem.rest.pathresolvers.DefaultPathParamResolver;
import org.kogito.workitem.rest.pathresolvers.PathParamResolver;
import org.kogito.workitem.rest.resulthandlers.DefaultRestWorkItemHandlerResult;
import org.kogito.workitem.rest.resulthandlers.RestWorkItemHandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpMethod;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

import static org.kogito.workitem.rest.RestWorkItemHandlerUtils.getParam;
import static org.kogito.workitem.rest.RestWorkItemHandlerUtils.vertx;

@ApplicationScoped
public class RestServiceTaskDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestServiceTaskDelegate.class);
    private static final RestWorkItemHandlerResult DEFAULT_RESULT_HANDLER = new DefaultRestWorkItemHandlerResult();
    private static final RestWorkItemHandlerBodyBuilder DEFAULT_BODY_BUILDER = new DefaultWorkItemHandlerBodyBuilder();
    private static final ParamsDecorator DEFAULT_PARAMS_DECORATOR = new PrefixParamsDecorator();
    private static final PathParamResolver DEFAULT_PATH_PARAM_RESOLVER = new DefaultPathParamResolver();
    private static final Collection<AuthDecorator> DEFAULT_AUTH_DECORATORS = Arrays
            .asList(new ApiKeyAuthDecorator(), new BasicAuthDecorator(),
                    new BearerTokenAuthDecorator());
    private WebClient client;

    public RestServiceTaskDelegate() {
    }

    @Inject
    public RestServiceTaskDelegate(final Vertx vertx) {
        client = WebClient.create(vertx(vertx));
    }

    //@Blocking
    public User execute(final DefaultRestMeta restMeta, final String username,
            final KogitoProcessContext context) {

        LOGGER.debug("Execute rest");
        final InternalKogitoWorkItem workItem = ((WorkItemNodeInstance) context.getNodeInstance())
                .getWorkItem();
        final Map<String, Object> parameters = restMeta.toMap();
        if (StringUtils.isNotBlank(username)) {
            parameters.put(DefaultRestMeta.USERNAME_PRM, username);
        }
        String endPoint = getParam(parameters, DefaultRestMeta.URL_PRM, String.class, null);
        if (endPoint == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter " + DefaultRestMeta.URL_PRM);
        }
        final HttpMethod method = getParam(parameters, DefaultRestMeta.METHOD_PRM, HttpMethod.class,
                HttpMethod.GET);
        final String host = getParam(parameters, DefaultRestMeta.HOST_PRM, String.class,
                "localhost");
        final int port = getParam(parameters, DefaultRestMeta.PORT_PRM, Integer.class, 80);
        LOGGER.debug("Filtered parameters are {}", parameters);
        // create request
        endPoint = DEFAULT_PATH_PARAM_RESOLVER.apply(endPoint, parameters);
        final String path = endPoint.replace(" ", "%20");//fix issue with spaces in the path
        final HttpRequest<Buffer> request = client.request(method, port, host, path);
        if (StringUtils.isNotBlank(restMeta.getAwait())) {
            request.putHeader("await", restMeta.getAwait());
        } else {
            ConfigProvider.getConfig()
                    .getOptionalValue("rest.response.default.await", String.class)
                    .ifPresent(await -> request.putHeader("await", await));
        }
        DEFAULT_AUTH_DECORATORS.forEach(d -> d.decorate(workItem, parameters, request));
        DEFAULT_PARAMS_DECORATOR.decorate(workItem, parameters, request);
        final HttpResponse<Buffer> response =
                method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT) ? request
                        .sendJsonAndAwait(DEFAULT_BODY_BUILDER.apply(parameters)) : request.sendAndAwait();
        return (User) DEFAULT_RESULT_HANDLER.apply(response, User.class);
    }
}
