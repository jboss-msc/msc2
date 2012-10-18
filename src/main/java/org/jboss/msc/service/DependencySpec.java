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

package org.jboss.msc.service;

import java.util.ArrayList;
import java.util.List;
import org.jboss.msc.value.WritableValue;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class DependencySpec<T> {
    private final ServiceContainer container;
    private final ServiceName name;
    private final DependencyFlag[] flags;
    private final List<WritableValue<? super T>> injections = new ArrayList<WritableValue<? super T>>();

    DependencySpec(final ServiceContainer container, final ServiceName name, final DependencyFlag[] flags) {
        this.container = container;
        this.name = name;
        this.flags = flags;
    }

    public ServiceContainer getContainer() {
        return container;
    }

    public ServiceName getName() {
        return name;
    }

    public DependencyFlag[] getFlags() {
        return flags;
    }

    public List<WritableValue<? super T>> getInjections() {
        return injections;
    }

    public void addInjection(final WritableValue<? super T> injector) {
        injections.add(injector);
    }
}
