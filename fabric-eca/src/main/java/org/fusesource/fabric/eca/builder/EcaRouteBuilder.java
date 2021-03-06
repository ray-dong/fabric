/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 FuseSource Corporation, a Progress Software company. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").
 * You may not use this file except in compliance with the License. You can obtain
 * a copy of the License at http://www.opensource.org/licenses/CDDL-1.0.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at resources/META-INF/LICENSE.txt.
 *
 */
package org.fusesource.fabric.eca.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EndpointHelper;
import org.fusesource.fabric.eca.model.EcaRoutesDefinition;
import org.fusesource.fabric.eca.model.EcaDefinition;
import org.fusesource.fabric.eca.model.EcaRouteDefinition;

public abstract class EcaRouteBuilder extends RouteBuilder {

    private EcaRoutesDefinition routeCollection = new EcaRoutesDefinition();

    public EcaRouteBuilder() {
        super();
    }

    public EcaRouteBuilder(CamelContext context) {
        super(context);
    }

    @Override
    public String toString() {
        return getRouteCollection().toString();
    }

    public EcaRouteDefinition eca(String name) {
        EcaRouteDefinition answer = getRouteCollection().cep(name);
        configureRoute(answer);
        return answer;
    }

    public void setRouteCollection(RoutesDefinition routeCollection) {
        this.routeCollection = new EcaRoutesDefinition(routeCollection);
    }

    public EcaRoutesDefinition getRouteCollection() {
        return this.routeCollection;
    }

    @Override
    protected void populateRoutes() throws Exception {
        super.populateRoutes();

        List<EcaRouteDefinition> ecaRouteDefinitions = new ArrayList<EcaRouteDefinition>();
        List<RouteDefinition> routes = getRouteCollection().getRoutes();

        for (RouteDefinition route : routes) {

            if (route instanceof EcaRouteDefinition) {
                EcaRouteDefinition ecaRouteDefinition = (EcaRouteDefinition) route;
                /*
            validate routes - this ensures routeId is correctly set for each EcaDefinition if RouteDefinitions are
            used to designate the route instead of an actual routeId string in the CEP expression.
            N.B. do not true to get the routeId before this stage e.g. don't Log or print the route!!
         */
                if (ecaRouteDefinition.getEcaDefinition() != null) {
                    ecaRouteDefinition.getEcaDefinition().validate(getContext());
                    ecaRouteDefinitions.add(ecaRouteDefinition);
                }
            }

        }

        for (EcaRouteDefinition ecaRouteDefinition : ecaRouteDefinitions) {
            List<String> targetIds = ecaRouteDefinition.getEcaDefinition().getTargetIds(getContext());
            for (final String targetId : targetIds) {
                RouteDefinition rd = getRouteCollection().getRouteDefinition(targetId);

                if (rd != null) {
                    if (!isWired(ecaRouteDefinition.getEcaDefinition(), rd)) {
                        rd.getOutputs().add(new ToDefinition(ecaRouteDefinition.getEcaDefinition().getUri()));
                    }
                } else {
                    //it could be and endpoint
                    Endpoint endpoint = resolveEndpoint(targetId);
                    if (endpoint != null) {
                        //find a route
                        String routeId = EndpointHelper.getRouteIdFromEndpoint(endpoint);
                        if (routeId != null) {
                            rd = getRouteCollection().getRouteDefinition(targetId);
                            if (!isWired(ecaRouteDefinition.getEcaDefinition(), rd)) {
                                rd.getOutputs().add(new ToDefinition(ecaRouteDefinition.getEcaDefinition().getUri()));
                            }
                        } else {
                            //create a route for it
                            RouteDefinition newRoute = getRouteCollection().from(endpoint);
                            newRoute.setId(targetId);
                            //setup a route
                            RoutePolicy routePolicy = new RoutePolicy() {
                                public void onInit(Route route) {
                                }

                                public void onExchangeBegin(Route route, Exchange exchange) {
                                    exchange.setFromRouteId(targetId);
                                }

                                public void onExchangeDone(Route route, Exchange exchange) {
                                }
                            };
                            List<RoutePolicy> routePolicies = new ArrayList<RoutePolicy>();
                            routePolicies.add(routePolicy);
                            newRoute.setRoutePolicies(routePolicies);
                            newRoute.getOutputs().add(new ToDefinition(ecaRouteDefinition.getEcaDefinition().getUri()));
                            getRouteCollection().getRoutes().add(newRoute);
                            getContext().addRouteDefinition(newRoute);
                        }
                    } else {
                        throw new IllegalArgumentException("Target " + targetId + " isn't a routeId or an endpoint");
                    }

                }

            }
        }

    }

    protected Endpoint resolveEndpoint(String targetId) {
        Endpoint result = getContext().getRegistry().lookup(targetId, Endpoint.class);
        if (result == null) {
            result = CamelContextHelper.getMandatoryEndpoint(getContext(), targetId);
        }
        return result;
    }

    protected boolean isWired(EcaDefinition ecaDefinition, RouteDefinition routeDefinition) {
        for (ProcessorDefinition pd : routeDefinition.getOutputs()) {
            if (pd instanceof ToDefinition) {
                ToDefinition td = (ToDefinition) pd;
                String uri = td.getUriOrRef();
                if (uri != null && uri.indexOf(ecaDefinition.getUri()) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

}
