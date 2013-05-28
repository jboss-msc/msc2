/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.msc.service;

import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.txn.ServiceContext;
import org.jboss.msc.txn.TaskController;
import org.jboss.msc.txn.Transaction;

/**
 * Dependency implementation. 
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 * @param <T>
 */
final class  SimpleDependency<T> implements Dependency<T> {
    /**
     * The dependency registration.
     */
    private final Registration dependencyRegistration;
    /**
     * The incoming dependency service controller.
     */
    private ServiceController<?> dependentController;
    /**
     * Indicates if this dependency is required to be UP. If false, indicates that this dependency is required to be
     * down in order for the dependency to be satisfied.
     */
    private final boolean dependencyUp;
    private boolean dependencySatisfied;
    /**
     * Indicates if the dependency should be demanded to be satisfied when service is attempting to start.
     */
    private final boolean propagateDemand;
    /**
     * List of injections.
     */
    private final Injector<? super T>[] injections;

    SimpleDependency(final Injector<? super T>[] injections, final Registration dependencyRegistration, final boolean dependencyUp, final boolean propagateDemand) {
        this.injections = injections;
        this.dependencyRegistration = dependencyRegistration;
        this.dependencyUp = dependencyUp;
        this.propagateDemand = propagateDemand;
    }

    @Override
    public synchronized void setDependent(ServiceController<?> dependentController, Transaction transaction, ServiceContext context) {
        this.dependentController = dependentController;
        if (isDependencySatisfied()) {
            dependencySatisfied = true;
            dependentController.dependencySatisfied(transaction, context);
        }
        dependencyRegistration.addIncomingDependency(transaction, context, this);
    }

    @Override
    public synchronized void clearDependent(Transaction transaction, ServiceContext context) {
        dependencyRegistration.removeIncomingDependency(transaction, context, this);
    }

    private final boolean isDependencySatisfied() {
        final ServiceController<?> dependencyController = dependencyRegistration.getController();
        return (dependencyUp && dependencyController != null && dependencyController.getState() == State.UP) ||
                (!dependencyUp && (dependencyController == null || dependencyController.getState() != State.UP));
    }

    @Override
    public Registration getDependencyRegistration() {
        return dependencyRegistration;
    }

    @Override
    public void performInjections() {
        assert dependencyRegistration.getController() != null;
        assert dependencyRegistration.getController().getValue() != null;
        for (Injector <? super T> injection: injections) {
            // TODO injection.setValue();
        }
    }

    /**
     * Demand this dependency to be satisfied.
     * 
     * @param transaction the active transaction
     */
    @Override
    public void demand(Transaction transaction, ServiceContext context) {
        if (propagateDemand) {
            dependencyRegistration.addDemand(transaction, context, dependencyUp);
        }
    }

    /**
     * Remove demand for this dependency to be satisfied.
     * 
     * @param transaction the active transaction
     */
    @Override
    public void undemand(Transaction transaction, ServiceContext context) {
        if (propagateDemand) {
            dependencyRegistration.removeDemand(transaction, context, dependencyUp);
        }
    }

    @Override
    public synchronized TaskController<?> dependencyAvailable(boolean dependencyUp, Transaction transaction, ServiceContext context) {
        if (this.dependencyUp == dependencyUp) {
            dependencySatisfied = true;
            return dependentController.dependencySatisfied(transaction, context);
        }
        return null;
    }

    @Override
    public synchronized TaskController<?> dependencyUnavailable(Transaction transaction, ServiceContext context) {
        if (dependencySatisfied) {
            dependencySatisfied = false;
            return dependentController.dependencyUnsatisfied(transaction, context);
        }
        return null;
    }

    @Override
    public void dependencyReplacementStarted(Transaction transaction) {
        // do nothing
    }

    @Override
    public void dependencyReplacementConcluded(Transaction transaction) {
        // do nothing
    }
}
