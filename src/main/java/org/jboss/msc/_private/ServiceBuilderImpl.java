/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.jboss.msc._private;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.txn.ServiceContext;
import org.jboss.msc.txn.TaskBuilder;
import org.jboss.msc.txn.TaskController;
import org.jboss.msc.txn.Transaction;

/**
 * A service builder.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceBuilderImpl<T> implements ServiceBuilder<T> {
    private static final DependencyFlag[] noFlags = new DependencyFlag[0];
    private final ServiceRegistryImpl registry;
    private final ServiceName name;
    private final Set<ServiceName> aliases = new HashSet<ServiceName>(0);
    private final Service<T> service;
    private final Map<ServiceName, DependencySpec<?>> specs = new LinkedHashMap<ServiceName, DependencySpec<?>>();
    private final boolean replacement;
    private final Transaction transaction;
    private ServiceMode mode;
    private TaskController<ServiceController<T>> installTask;
    private DependencySpec<?> parentDependencySpec;
    private final Set<TaskController<?>> taskDependencies = new HashSet<TaskController<?>>(0);

    public ServiceBuilderImpl(final ServiceRegistry registry, final Transaction transaction, final ServiceName name, final Service<T> service) {
        this(registry, transaction, name, service, false);
    }

    public ServiceBuilderImpl(final ServiceRegistry registry, final Transaction transaction, final ServiceName name, final Service<T> service, final boolean replaceService) {
        this.transaction = transaction;
        this.registry = (ServiceRegistryImpl)registry;
        this.name = name;
        this.service = service;
        this.replacement = replaceService;
        this.mode = ServiceMode.ACTIVE;
    }

    /**
     * Set the service mode.
     *
     * @param mode the service mode
     */
    public ServiceBuilder<T> setMode(final ServiceMode mode) {
        checkAlreadyInstalled();
        if (mode == null) {
            MSCLogger.SERVICE.methodParameterIsNull("mode");
        }
        this.mode = mode;
        return this;
    }

    /**
     * Add aliases for this service.
     *
     * @param aliases the service names to use as aliases
     * @return the builder
     */
    public ServiceBuilderImpl<T> addAliases(final ServiceName... aliases) {
        checkAlreadyInstalled();
        if (aliases != null) for (final ServiceName alias : aliases) {
            if (alias != null && !alias.equals(name)) {
                this.aliases.add(alias);
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceName name) {
        return addDependencyInternal(registry, name, null, (DependencyFlag[])null);
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceName name, final DependencyFlag... flags) {
        return addDependencyInternal(registry, name, null, flags);
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceName name, final Injector<?> injector) {
        return addDependencyInternal(registry, name, injector, (DependencyFlag[])null);
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceName name, final Injector<?> injector, final DependencyFlag... flags) {
        return addDependencyInternal(registry, name, injector, flags);
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceRegistry registry, final ServiceName name) {
        return addDependencyInternal(registry, name, null, (DependencyFlag[])null);
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceRegistry registry, final ServiceName name, final DependencyFlag... flags) {
        return addDependencyInternal(registry, name, null, flags);
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceRegistry registry, final ServiceName name, final Injector<?> injector) {
        return addDependencyInternal(registry, name, injector, (DependencyFlag[])null);
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(final ServiceRegistry registry, final ServiceName name, final Injector<?> injector, final DependencyFlag... flags) {
        return addDependencyInternal(registry, name, injector, flags);
    }

    private ServiceBuilder<T> addDependencyInternal(final ServiceRegistry registry, final ServiceName name, final Injector<?> injector, final DependencyFlag... flags) {
        checkAlreadyInstalled();
        if (registry == null) {
            MSCLogger.SERVICE.methodParameterIsNull("registry");
        }
        if (name == null) {
            MSCLogger.SERVICE.methodParameterIsNull("name");
        }
        final DependencySpec<?> spec = new DependencySpec<Object>((ServiceRegistryImpl)registry, name, flags != null ? flags : noFlags);
        //spec.addInjection(injector);
        addDependencySpec(spec, name, flags != null ? flags : noFlags);
        return this;
    }

    private void addDependencySpec(DependencySpec<?> dependencySpec, ServiceName name, DependencyFlag... flags) {
        for (DependencyFlag flag: flags) {
            if (flag == DependencyFlag.PARENT) {
                synchronized (this) {
                    if (parentDependencySpec != null) {
                        throw new IllegalStateException("Service cannot have more than one parent dependency");
                    }
                    parentDependencySpec = dependencySpec;
                    specs.remove(name);
                    return;
                }
            }
        }
        if (parentDependencySpec != null && name == parentDependencySpec.getName()) {
            parentDependencySpec = null;
        }
        specs.put(name, dependencySpec);
    }

    /**
     * {@inheritDoc}
     */
    public ServiceBuilder<T> addDependency(TaskController<?> task) {
        checkAlreadyInstalled();
        taskDependencies.add(task);
        return this;
    }

    /**
     * Initiate installation of this service as configured.  If the service was already installed, this method has no
     * effect.
     */
    public synchronized void install() {
        checkAlreadyInstalled();
        final TaskBuilder<ServiceController<T>> taskBuilder = transaction.newTask(new ServiceInstallTask<T>(transaction, this));
        taskBuilder.addDependencies(taskDependencies);
        if (replacement) {
            startReplacement(taskBuilder, transaction);
        }
        installTask = taskBuilder.release();
    }

    /**
     * Manually rolls back this service installation.  If the service was already installed, it will be removed.
     * 
     * @param transaction active transaction
     */
    public void remove(Transaction transaction) {
        final ServiceController<?> serviceController;
        synchronized (this) {
            serviceController = installTask.getResult();
        }
        if (serviceController != null) {
            serviceController.remove(transaction);
        }
    }

    private synchronized void checkAlreadyInstalled() {
        if (installTask != null) {
            throw new IllegalStateException("ServiceBuilder installation already requested.");
        }
    }

    /**
     * Performs installation of this service builder into registry.
     * 
     * @param transaction active transaction
     * @param context     the service context
     * @return            the installed service controller. May be {@code null} if the service is being created with a
     *                    parent dependency.
     */
    ServiceController<T> performInstallation(Transaction transaction, ServiceContext context) {
        if (parentDependencySpec != null) {
            parentDependencySpec.createDependency(this, transaction, context);
            return null;
        }
        return installController(null, transaction, context);
    }

    /**
     * Concludes service installation by creating and installing the service controller into the registry.
     * 
     * @param parentDependency parent dependency, if any
     * @param transaction      active transaction
     * @param context          the service context
     * 
     * @return the installed service controller
     */
    ServiceController<T> installController(Dependency<?> parentDependency, Transaction transaction, ServiceContext context) {
        final Registration registration = registry.getOrCreateRegistration(context, transaction, name);
        final ServiceName[] aliasArray = aliases.toArray(new ServiceName[aliases.size()]);
        final Registration[] aliasRegistrations = new Registration[aliasArray.length];
        int i = 0; 
        for (ServiceName alias: aliases) {
            aliasRegistrations[i++] = registry.getOrCreateRegistration(context, transaction, alias);
        }
        i = 0;
        final Dependency<?>[] dependencies;
        if (parentDependency == null) {
            dependencies = new Dependency<?>[specs.size()];
        } else {
            dependencies = new Dependency<?>[specs.size() + 1];
            dependencies[i++] = parentDependency;
        }
        for (DependencySpec<?> spec : specs.values()) {
            dependencies[i++] = spec.createDependency(this, transaction, context);
        }
        final ServiceController<T> serviceController =  new ServiceController<T>(registration, aliasRegistrations, service, mode, dependencies, transaction, context);
        serviceController.install(transaction, context);
        if (replacement) {
            concludeReplacement(serviceController, transaction);
        }
        return serviceController;
    }

    private void startReplacement(TaskBuilder<ServiceController<T>> serviceInstallTaskBuilder, Transaction transaction) {
        startReplacement(registry.getOrCreateRegistration(transaction,transaction, name), serviceInstallTaskBuilder, transaction);
        for (ServiceName alias: aliases) {
            startReplacement(registry.getOrCreateRegistration(transaction, transaction, alias), serviceInstallTaskBuilder, transaction);
        }
    }

    private void startReplacement(Registration registration, TaskBuilder<ServiceController<T>> serviceInstallTaskBuilder, Transaction transaction) {
        for (Dependency<?> dependency: registration.getIncomingDependencies()) {
            dependency.dependencyReplacementStarted(transaction);
        }
        ServiceController<?> serviceController = registration.getController();
        if (serviceController != null) {
            serviceInstallTaskBuilder.addDependency(serviceController.remove(transaction));
        }
    }

    private void concludeReplacement(ServiceController<?> serviceController, Transaction transaction) {
        concludeReplacement(serviceController.getPrimaryRegistration(), transaction);
        for (Registration registration: serviceController.getAliasRegistrations()) {
            concludeReplacement(registration,  transaction);
        }
    }

    private void concludeReplacement(Registration registration, Transaction transaction) {
        for (Dependency<?> dependency: registration.getIncomingDependencies()) {
            dependency.dependencyReplacementConcluded(transaction);
        }
    }
}