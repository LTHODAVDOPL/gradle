/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.internal.provider.runner;

import com.google.common.base.MoreObjects;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType;
import org.gradle.configuration.ApplyScriptPluginBuildOperationType;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier;
import org.gradle.tooling.internal.provider.events.DefaultBinaryPluginIdentifier;
import org.gradle.tooling.internal.provider.events.DefaultScriptPluginIdentifier;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

class PluginApplicationTracker implements BuildOperationListener {

    private static final String PROJECT_TARGET_TYPE = "project";

    private final Map<OperationIdentifier, PluginApplication> pluginApplications = new ConcurrentHashMap<>();
    private final BuildOperationParentTracker parentTracker;

    PluginApplicationTracker(BuildOperationParentTracker parentTracker) {
        this.parentTracker = parentTracker;
    }

    public PluginApplication getPluginApplication(OperationIdentifier id) {
        return pluginApplications.get(id);
    }

    public PluginApplication findCurrentPluginApplication(OperationIdentifier id) {
        OperationIdentifier applicationOperationId = parentTracker.findClosestAncestor(id, pluginApplications::containsKey);
        if (applicationOperationId != null) {
            return pluginApplications.get(applicationOperationId);
        }
        return null;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (buildOperation.getDetails() instanceof ApplyPluginBuildOperationType.Details) {
            ApplyPluginBuildOperationType.Details details = (ApplyPluginBuildOperationType.Details) buildOperation.getDetails();
            add(buildOperation, details.getTargetType(), details.getApplicationId(), () -> toBinaryPluginIdentifier(details));
        } else if (buildOperation.getDetails() instanceof ApplyScriptPluginBuildOperationType.Details) {
            ApplyScriptPluginBuildOperationType.Details details = (ApplyScriptPluginBuildOperationType.Details) buildOperation.getDetails();
            add(buildOperation, details.getTargetType(), details.getApplicationId(), () -> toScriptPluginIdentifier(details));
        }
    }

    private void add(BuildOperationDescriptor buildOperation, String targetType, long applicationId, Supplier<InternalPluginIdentifier> pluginSupplier) {
        if (PROJECT_TARGET_TYPE.equals(targetType)) {
            pluginApplications.put(buildOperation.getId(), new PluginApplication(applicationId, pluginSupplier.get()));
        }
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        pluginApplications.remove(buildOperation.getId());
    }

    private InternalBinaryPluginIdentifier toBinaryPluginIdentifier(ApplyPluginBuildOperationType.Details details) {
        String className = details.getPluginClass().getName();
        String pluginId = details.getPluginId();
        String displayName = MoreObjects.firstNonNull(pluginId, className);
        return new DefaultBinaryPluginIdentifier(displayName, className, pluginId);
    }

    private InternalScriptPluginIdentifier toScriptPluginIdentifier(ApplyScriptPluginBuildOperationType.Details details) {
        String fileString = details.getFile();
        if (fileString != null) {
            File file = new File(fileString);
            return new DefaultScriptPluginIdentifier(file.getName(), file.toURI());
        }
        String uriString = details.getUri();
        if (uriString != null) {
            URI uri = URI.create(uriString);
            return new DefaultScriptPluginIdentifier(FilenameUtils.getName(uri.getPath()), uri);
        }
        return null;
    }

    static class PluginApplication {

        private final long applicationId;
        private final InternalPluginIdentifier plugin;

        PluginApplication(long applicationId, InternalPluginIdentifier plugin) {
            this.applicationId = applicationId;
            this.plugin = plugin;
        }

        public long getApplicationId() {
            return applicationId;
        }

        public InternalPluginIdentifier getPlugin() {
            return plugin;
        }

    }

}
