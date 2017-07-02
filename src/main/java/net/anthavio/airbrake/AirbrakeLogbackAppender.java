/**
 * Copyright (C) 2014 Anthavio (http://dev.anthavio.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.anthavio.airbrake;

import java.util.LinkedList;

import airbrake.AirbrakeNotifier;
import airbrake.Backtrace;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import net.anthavio.airbrake.http.HttpServletRequestEnhancerFactory;
import net.anthavio.airbrake.http.RequestEnhancer;
import net.anthavio.airbrake.http.RequestEnhancerFactory;

/**
 *
 * @author martin.vanek
 *
 */
public class AirbrakeLogbackAppender extends AppenderBase<ILoggingEvent> {

    public enum Notify {
        ALL, EXCEPTIONS, OFF;
    }

    private final AirbrakeNotifier airbrakeNotifier;

    private String apiKey;

    private String env;

    private String requestEnhancerFactory;

    private RequestEnhancer requestEnhancer;

    private Notify notify = Notify.EXCEPTIONS; // default compatible with airbrake-java

    private boolean enabled = true;

    private Backtrace backtraceBuilder = new Backtrace(new LinkedList<String>());

    public AirbrakeLogbackAppender() {
        airbrakeNotifier = new AirbrakeNotifier();
    }

    protected AirbrakeLogbackAppender(AirbrakeNotifier airbrakeNotifier) {
        this.airbrakeNotifier = airbrakeNotifier;
    }

    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(final String env) {
        this.env = env;
    }

    public Backtrace getBacktraceBuilder() {
        return backtraceBuilder;
    }

    public void setBacktraceBuilder(Backtrace backtraceBuilder) {
        this.backtraceBuilder = backtraceBuilder;
    }

    public String getRequestEnhancerFactory() {
        return requestEnhancerFactory;
    }

    public void setRequestEnhancerFactory(String requestEnhancerFactory) {
        this.requestEnhancerFactory = requestEnhancerFactory;
    }

    public void setUrl(final String url) {
        //TODO this should do addError instead of throwing exception
        if (url == null || !url.startsWith("http")) {
            throw new IllegalArgumentException("Wrong url: " + url);
        }
        airbrakeNotifier.setUrl(url);
    }

    public Notify getNotify() {
        return notify;
    }

    public void setNotify(Notify notify) {
        this.notify = notify;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void append(final ILoggingEvent event) {
        if (!enabled || notify == Notify.OFF) {
            return;
        }

        IThrowableProxy proxy;
        if ((proxy = event.getThrowableProxy()) != null) {
            // Exception are always notified
            Throwable throwable = ((ThrowableProxy) proxy).getThrowable();
            AirbrakeNoticeBuilderUsingFilteredSystemProperties builder = new AirbrakeNoticeBuilderUsingFilteredSystemProperties(apiKey, backtraceBuilder, throwable, env);
            if (requestEnhancer != null) {
                requestEnhancer.enhance(builder);
            }
            airbrakeNotifier.notify(builder.newNotice());

        } else if (notify == Notify.ALL) {
            // others only if ALL is set
            StackTraceElement[] stackTrace = event.getCallerData();
            AirbrakeNoticeBuilderUsingFilteredSystemProperties builder = new AirbrakeNoticeBuilderUsingFilteredSystemProperties(apiKey, event.getFormattedMessage(), stackTrace[0], env);
            if (requestEnhancer != null) {
                requestEnhancer.enhance(builder);
            }
            airbrakeNotifier.notify(builder.newNotice());
        }
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void start() {
        if (apiKey == null || apiKey.isEmpty()) {
            addError("API key not set for the appender named [" + name + "].");
        }
        if (env == null || env.isEmpty()) {
            addError("Environment not set for the appender named [" + name + "].");
        }
        if (requestEnhancerFactory != null) {
            RequestEnhancerFactory factory = null;
            try {
                factory = (RequestEnhancerFactory) Class.forName(requestEnhancerFactory).newInstance();
            } catch (Exception x) {
                throw new IllegalStateException("Cannot create " + requestEnhancerFactory, x);
            }
            requestEnhancer = factory.get();
        } else if (HttpServletRequestEnhancerFactory.isServletApi()) {
            requestEnhancer = new HttpServletRequestEnhancerFactory().get();
        }
        super.start();
    }

}
