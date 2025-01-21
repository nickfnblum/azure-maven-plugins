/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.action;

import com.microsoft.azure.toolkit.lib.account.IAccount;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.operation.OperationBase;
import com.microsoft.azure.toolkit.lib.common.operation.OperationBundle;
import com.microsoft.azure.toolkit.lib.common.view.IView;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.PropertyKey;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings({"unused", "UnresolvedPropertyKey"})
@Slf4j
@Accessors(chain = true)
public class Action<D> implements Cloneable {
    public static final String SOURCE = "ACTION_SOURCE";
    public static final String PLACE = "action_place";
    public static final String EMPTY_PLACE = "empty";
    public static final String RESOURCE_TYPE = "resourceType";
    public static final Id<Object> AUTHENTICATE = Id.of("user/account.authenticate");
    public static final Id<Consumer<IAccount>> REQUIRE_AUTH = Id.of("user/common.authorize_action");
    public static final Id<Object> TRY_AZURE = Action.Id.of("user/account.try_azure");
    public static final Id<Object> SELECT_SUBS = Action.Id.of("user/account.select_subs");
    public static final Id<Object> OPEN_AZURE_SETTINGS = Action.Id.of("user/common.open_azure_settings");
    public static final Id<Object> DISABLE_AUTH_CACHE = Action.Id.of("user/account.disable_auth_cache");
    public static final Action.Id<Object> SIGN_IN = Action.Id.of("user/common.sign_in");
    public static final Action.Id<Object> SIGN_OUT = Action.Id.of("user/common.sign_out");
    public static final Action.Id<String> OPEN_URL = Action.Id.of("user/common.open_url.url");

    public static final String COMMON_PLACE = "common";

    @Getter
    @Nonnull
    private final Id<D> id;
    @Nonnull
    private List<Pair<BiPredicate<D, ?>, BiConsumer<D, ?>>> handlers = new ArrayList<>();
    protected D target;
    @Nonnull
    Predicate<D> enableWhen = o -> true;
    BiPredicate<Object, String> visibleWhen = (o, place) -> true;
    Function<D, String> iconProvider;
    Function<D, String> labelProvider;
    Function<D, AzureString> titleProvider;
    @Nonnull
    List<Function<D, String>> titleParamProviders = new ArrayList<>();
    Function<D, Object> sourceProvider;
    Predicate<D> authRequiredProvider = Action::isAuthRequiredForAzureResource;

    /**
     * shortcuts for this action.
     * 1. directly bound to this action if it's IDE-specific type of shortcuts (e.g. {@code ShortcutSet} in IntelliJ).
     * 2. interpreted into native shortcuts first and then bound to this action if it's {@code String[]/String} (e.g. {@code "alt X"}).
     * 3. copy shortcuts from actions specified by this action id and then bound to this action if it's {@link Id} of another action.
     */
    @Setter
    @Getter
    private Object shortcut;

    public Action(@Nonnull Id<D> id) {
        this.id = id;
    }

    public IView.Label getView(D s) {
        return getView(s, COMMON_PLACE);
    }

    @Nonnull
    public IView.Label getView(D s, final String place) {
        final ActionInstance<D> instance = this.instantiate(s, null);
        return instance.getView(place);
    }

    /**
     * perform asynchronously
     */
    public void handle(D s) {
        this.handle(s, null);
    }

    /**
     * perform asynchronously
     */
    public void handle(D s, Object e) {
        final ActionInstance<D> instance = this.instantiate(s, e);
        instance.performAsync();
    }

    /**
     * perform asynchronously
     */
    public void handleSync(D s) {
        this.handleSync(s, null);
    }

    /**
     * perform asynchronously
     */
    public void handleSync(D s, Object e) {
        final ActionInstance<D> instance = this.instantiate(s, e);
        instance.perform();
    }

    public Action<D> enableWhen(@Nonnull Predicate<D> enableWhen) {
        this.enableWhen = enableWhen;
        return this;
    }

    public Action<D> visibleWhen(@Nonnull Predicate<Object> visibleWhen) {
        this.visibleWhen = (object, ignore) -> visibleWhen.test(object);
        return this;
    }

    public Action<D> visibleWhen(@Nonnull BiPredicate<Object, String> visibleWhen) {
        this.visibleWhen = visibleWhen;
        return this;
    }

    public Action<D> withLabel(@Nonnull final String label) {
        this.labelProvider = (any) -> label;
        return this;
    }

    public Action<D> withLabel(@Nonnull final Function<D, String> labelProvider) {
        this.labelProvider = labelProvider;
        return this;
    }

    public Action<D> withIcon(@Nonnull final String icon) {
        this.iconProvider = (any) -> icon;
        return this;
    }

    public Action<D> withIcon(@Nonnull final Function<D, String> iconProvider) {
        this.iconProvider = iconProvider;
        return this;
    }

    public Action<D> withTitle(@Nonnull final AzureString title) {
        this.titleProvider = (any) -> title;
        return this;
    }

    public Action<D> withShortcut(@Nonnull final Object shortcut) {
        this.shortcut = shortcut;
        return this;
    }

    public Action<D> withHandler(@Nonnull Consumer<D> handler) {
        this.handlers.add(Pair.of((d, e) -> true, (d, e) -> handler.accept(d)));
        return this;
    }

    public <E> Action<D> withHandler(@Nonnull BiConsumer<D, E> handler) {
        this.handlers.add(Pair.of((d, e) -> true, handler));
        return this;
    }

    public Action<D> withHandler(@Nonnull Predicate<D> condition, @Nonnull Consumer<D> handler) {
        this.handlers.add(Pair.of((d, e) -> condition.test(d), (d, e) -> handler.accept(d)));
        return this;
    }

    public <E> Action<D> withHandler(@Nonnull BiPredicate<D, E> condition, @Nonnull BiConsumer<D, E> handler) {
        this.handlers.add(Pair.of(condition, handler));
        return this;
    }

    public Action<D> withAuthRequired(boolean authRequired) {
        this.authRequiredProvider = ignore -> authRequired;
        return this;
    }

    public Action<D> withAuthRequired(@Nonnull Predicate<D> authRequiredProvider) {
        this.authRequiredProvider = authRequiredProvider;
        return this;
    }

    public Action<D> withIdParam(@Nonnull final String titleParam) {
        this.titleParamProviders.add((d) -> titleParam);
        return this;
    }

    public Action<D> withIdParam(@Nonnull final Function<D, String> titleParamProvider) {
        this.titleParamProviders.add(titleParamProvider);
        return this;
    }

    public Action<D> withSource(@Nonnull final Object source) {
        this.sourceProvider = (any) -> source;
        return this;
    }

    public Action<D> withSource(@Nonnull final Function<D, Object> sourceProvider) {
        this.sourceProvider = sourceProvider;
        return this;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    BiConsumer<D, Object> getHandler(D s, Object e) {
        if (!this.visibleWhen.test(s, COMMON_PLACE) && !this.enableWhen.test(s)) {
            return null;
        }
        for (int i = this.handlers.size() - 1; i >= 0; i--) {
            final Pair<BiPredicate<D, ?>, BiConsumer<D, ?>> p = this.handlers.get(i);
            final BiPredicate<D, Object> condition = (BiPredicate<D, Object>) p.getKey();
            final BiConsumer<D, Object> handler = (BiConsumer<D, Object>) p.getValue();
            try {
                if (condition.test(s, e)) {
                    return handler;
                }
            } catch (final Exception ignored) {
            }
        }
        return null;
    }

    public Action<D> bind(D s) { // TODO: remove this method and use `instantiate` instead
        try {
            // noinspection unchecked
            final Action<D> clone = (Action<D>) this.clone();
            clone.handlers = new ArrayList<>(this.handlers);
            clone.titleParamProviders = new ArrayList<>(this.titleParamProviders);
            clone.target = s;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    public ActionInstance<D> instantiate(D s) {
        return this.instantiate(s, null);
    }

    @Nonnull
    public ActionInstance<D> instantiate(D s, Object event) {
        final D target = Optional.ofNullable(this.target).orElse(s);
        return new ActionInstance<>(this, target, event);
    }

    public void register(AzureActionManager am) {
        am.registerAction(this);
    }

    @Override
    public String toString() {
        return String.format("Action {id:'%s', bindTo: %s}", this.getId(), this.target);
    }

    @Getter
    public static class Id<D> extends OperationBase.Id {
        @Nonnull
        private final String id;

        private Id(@Nonnull String id) {
            super(id);
            this.id = id;
        }

        public static <D> Id<D> of(@PropertyKey(resourceBundle = OperationBundle.BUNDLE) @Nonnull String id) {
            assert StringUtils.isNotBlank(id) : "action id can not be blank";
            return new Id<>(id);
        }

        public String toString() {
            return id;
        }
    }

    @Getter
    @RequiredArgsConstructor
    @AllArgsConstructor
    public static class View implements IView.Label {
        public static View INVISIBLE = new View("", "", false, false, null);
        @Nonnull
        private final String label;
        private final String iconPath;
        private final boolean enabled;
        private boolean visible = true;
        @Nullable
        private AzureString title;

        public View(@Nonnull String label, String iconPath, boolean enabled, @Nullable AzureString title) {
            this(label, iconPath, enabled, true, title);
        }

        @Override
        public String getDescription() {
            return Optional.ofNullable(this.title).map(AzureString::toString).orElse(null);
        }

        @Override
        public void dispose() {
        }
    }

    public static Action<Void> retryFromFailure(@Nonnull Runnable handler) {
        return new Action<>(Id.<Void>of("user/common.retry"))
            .withHandler((v) -> handler.run())
            .withLabel("Retry");
    }

    public static <D> Boolean isAuthRequiredForAzureResource(@Nullable final D resource) {
        return resource instanceof AbstractAzResource && ((AbstractAzResource<?, ?, ?>) resource).isAuthRequired();
    }
}

