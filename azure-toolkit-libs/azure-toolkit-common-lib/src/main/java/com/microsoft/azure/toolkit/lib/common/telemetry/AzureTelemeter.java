/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.telemetry;

import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.operation.MethodOperation;
import com.microsoft.azure.toolkit.lib.common.operation.Operation;
import com.microsoft.azure.toolkit.lib.common.operation.OperationBundle;
import com.microsoft.azure.toolkit.lib.common.operation.OperationContext;
import com.microsoft.azure.toolkit.lib.common.operation.SimpleOperation;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetry.Properties;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetry.Property;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.PropertyKey;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AzureTelemeter {
    public static final String INFO_BUNDLE = "bundles.com.microsoft.azure.toolkit.info";

    public static final String SERVICE_NAME = "serviceName";
    public static final String OPERATION_NAME = "operationName";
    public static final String OP_ID = "op_id";
    public static final String OP_NAME = "op_name";
    public static final String OP_TYPE = "op_type";
    public static final String OP_PARENT_ID = "op_parentId";

    public static final String INFO_NAME = "info.name";
    public static final String INFO_SERVICE = "info.service";
    public static final String INFO_DETAILS = "info.details";
    public static final String ERROR_CODE = "error.error_code";
    public static final String ERROR_MSG = "error.error_msg";
    public static final String ERROR_ROOT_MSG = "error.root_error_message";
    public static final String ERROR_TYPE = "error.error_type";
    public static final String ERROR_CLASSNAME = "error.error_class_name";
    public static final String ERROR_ROOT_CLASSNAME = "error.root_error_class_name";
    public static final String ERROR_STACKTRACE = "error.error_stack_trace";
    @Nullable
    private static AzureTelemetryClient client = null;

    public static synchronized AzureTelemetryClient getClient() {
        if (client == null) {
            client = new AzureTelemetryClient();
        }
        return client;
    }

    public static void addCommonProperties(@Nonnull Map<String, String> commonProperties) {
        getClient().addDefaultProperties(commonProperties);
    }

    public static void addCommonProperty(@Nonnull String key, @Nonnull String value) {
        getClient().addDefaultProperty(key, value);
    }

    public static void setEventNamePrefix(@Nonnull String prefix) {
        getClient().setEventNamePrefix(prefix);
    }

    public static void afterCreate(@Nonnull final Operation op) {
        op.getContext().setTelemetryProperty(AzureTelemetry.OP_CREATE_AT, Instant.now().toString());
    }

    public static void beforeEnter(@Nonnull final Operation op) {
        op.getContext().setTelemetryProperty(AzureTelemetry.OP_ENTER_AT, Instant.now().toString());
    }

    public static void afterExit(@Nonnull final Operation op) {
        op.getContext().setTelemetryProperty(AzureTelemetry.OP_EXIT_AT, Instant.now().toString());
        AzureTelemeter.log(AzureTelemetry.Type.OP_END, serialize(op));
    }

    public static void onError(@Nonnull final Operation op, Throwable error) {
        op.getContext().setTelemetryProperty(AzureTelemetry.OP_EXIT_AT, Instant.now().toString());
        AzureTelemeter.log(AzureTelemetry.Type.ERROR, serialize(op), error);
    }

    public static void info(@Nonnull @PropertyKey(resourceBundle = INFO_BUNDLE) final String key) {
        AzureTelemeter.log(AzureTelemetry.Type.INFO, ImmutableMap.of(INFO_NAME, StringUtils.substringAfter(key, "."), INFO_SERVICE, StringUtils.substringBefore(key, ".")));
    }

    public static void info(@Nonnull @PropertyKey(resourceBundle = INFO_BUNDLE) final String key, final String details) {
        AzureTelemeter.log(AzureTelemetry.Type.INFO, ImmutableMap.of(INFO_NAME, StringUtils.substringAfter(key, "."), INFO_SERVICE, StringUtils.substringBefore(key, "."), INFO_DETAILS, details));
    }

    public static void info(@Nonnull @PropertyKey(resourceBundle = INFO_BUNDLE) final String key, Map<String, String> details) {
        final HashMap<String, String> map = new HashMap<>(details);
        map.put(INFO_NAME, StringUtils.substringAfter(key, "."));
        map.put(INFO_SERVICE, StringUtils.substringBefore(key, "."));
        AzureTelemeter.log(AzureTelemetry.Type.INFO, map);
    }

    public static void log(final AzureTelemetry.Type type, final String opId) {
        AzureTelemeter.log(type, serialize(new SimpleOperation(OperationBundle.description(opId), () -> null, null)));
    }

    public static void log(final AzureTelemetry.Type type, final AzureString op) {
        AzureTelemeter.log(type, serialize(new SimpleOperation(op, () -> null, null)));
    }

    public static void log(final AzureTelemetry.Type type, final String opId, final Throwable e) {
        AzureTelemeter.log(type, serialize(new SimpleOperation(OperationBundle.description(opId), () -> null, null)), e);
    }

    public static void log(final AzureTelemetry.Type type, final AzureString op, final Throwable e) {
        AzureTelemeter.log(type, serialize(new SimpleOperation(op, () -> null, null)), e);
    }

    public static void log(final AzureTelemetry.Type type, final Map<String, String> properties, final Throwable e) {
        if (Objects.nonNull(e)) {
            properties.putAll(serialize(e));
        }
        AzureTelemeter.log(type, properties);
    }

    public static void log(final AzureTelemetry.Type type, final Map<String, String> properties) {
        if (!StringUtils.equals(properties.get(OP_NAME), Operation.UNKNOWN_NAME)) {
            final String eventName = Optional.ofNullable(getClient().getEventNamePrefix()).orElse("AzurePlugin") + "/" + type.getName();
            getClient().trackEvent(eventName, properties, null);
        }
    }

    @Nonnull
    private static Map<String, String> serialize(@Nonnull final Operation op) {
        final OperationContext context = op.getContext();
        final Map<String, String> actionProperties = getActionProperties(op);
        final Optional<Operation> parent = Optional.ofNullable(op.getEffectiveParent());
        final Map<String, String> properties = new HashMap<>();
        final String name = op.getId().replaceAll("\\(.+\\)", "(***)"); // e.g. `internal/$appservice.list_file.dir`
        properties.put(OP_ID, op.getExecutionId());
        properties.put(OP_PARENT_ID, parent.map(Operation::getExecutionId).orElse("/"));
        properties.put(OP_NAME, name);
        properties.put(OP_TYPE, op.getType());
        properties.put(SERVICE_NAME, op.getServiceName());
        properties.put(OPERATION_NAME, op.getOperationName()); // "list_file"
        properties.putAll(actionProperties);
        if (op instanceof MethodOperation) {
            properties.putAll(getParameterProperties((MethodOperation) op));
        }
        properties.putAll(context.getTelemetryProperties());
        return properties;
    }

    private static Map<String, String> getParameterProperties(MethodOperation ref) {
        final HashMap<String, String> properties = new HashMap<>();
        final List<Triple<String, Parameter, Object>> args = ref.getInvocation().getArgs();
        for (final Triple<String, Parameter, Object> arg : args) {
            final Parameter param = arg.getMiddle();
            final Object value = arg.getRight();
            Optional.ofNullable(param.getAnnotation(Property.class))
                .map(Property::value)
                .map(n -> Property.PARAM_NAME.equals(n) ? param.getName() : n)
                .ifPresent((name) -> properties.put(name, Optional.ofNullable(value).map(Object::toString).orElse("")));
            Optional.ofNullable(param.getAnnotation(Properties.class))
                .map(Properties::value)
                .map(AzureTelemeter::instantiate)
                .map(converter -> converter.convert(value))
                .ifPresent(properties::putAll);
        }
        return properties;
    }

    @Nonnull
    private static Map<String, String> getActionProperties(@Nonnull Operation operation) {
        return Optional.ofNullable(operation.getActionParent())
            .map(Operation::getContext)
            .map(OperationContext::getTelemetryProperties)
            .orElse(new HashMap<>());
    }

    @SneakyThrows
    private static <U> U instantiate(Class<? extends U> clazz) {
        return clazz.newInstance();
    }

    @Nonnull
    private static HashMap<String, String> serialize(@Nonnull Throwable e) {
        final HashMap<String, String> properties = new HashMap<>();
        final ErrorType type = ErrorType.userError; // TODO: (@wangmi & @Hanxiao.Liu)decide error type based on the type of ex.
        properties.put(ERROR_CLASSNAME, e.getClass().getName());
        properties.put(ERROR_TYPE, type.name());
        properties.put(ERROR_MSG, e.getMessage());
        Optional.ofNullable(ExceptionUtils.getRootCause(e)).ifPresent(root -> {
            properties.put(ERROR_ROOT_MSG, root.getMessage());
            properties.put(ERROR_ROOT_CLASSNAME, root.getClass().getName());
        });
        properties.put(ERROR_STACKTRACE, ExceptionUtils.getStackTrace(e));
        return properties;
    }

    private enum ErrorType {
        userError,
        systemError,
        serviceError,
        toolError,
        unclassifiedError
    }
}
