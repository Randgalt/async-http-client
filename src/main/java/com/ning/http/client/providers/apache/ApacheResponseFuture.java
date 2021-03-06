/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */
package com.ning.http.client.providers.apache;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.FutureImpl;
import com.ning.http.client.Request;
import org.apache.commons.httpclient.HttpMethodBase;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public class ApacheResponseFuture<V> implements FutureImpl<V> {

    private Future<V> innerFuture;
    private final AsyncHandler<V> asyncHandler;
    private final int responseTimeoutInMs;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean timedOut = new AtomicBoolean(false);
    private final AtomicBoolean isDone = new AtomicBoolean(false);
    private final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
    private final AtomicLong touch = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean contentProcessed = new AtomicBoolean(false);
    private final Request request;
    private final HttpMethodBase method;
    private Future<?> reaperFuture;

    public ApacheResponseFuture(AsyncHandler<V> asyncHandler, int responseTimeoutInMs, Request request, HttpMethodBase method) {
        this.asyncHandler = asyncHandler;
        this.responseTimeoutInMs = responseTimeoutInMs == -1 ? Integer.MAX_VALUE : responseTimeoutInMs;
        this.request = request;
        this.method = method;
    }

    protected void setInnerFuture(Future<V> innerFuture) {
        this.innerFuture = innerFuture;
    }

    public void done(Callable callable) {
        isDone.set(true);
        if (reaperFuture != null) {
            reaperFuture.cancel(true);
        }
    }

    protected void setReaperFuture(Future<?> reaperFuture) {
        if (this.reaperFuture != null) {
            this.reaperFuture.cancel(true);
        }
        this.reaperFuture = reaperFuture;
    }

    @Override
    public String toString() {
        return "ApacheResponseFuture{" +
                "innerFuture=" + innerFuture +
                ", asyncHandler=" + asyncHandler +
                ", responseTimeoutInMs=" + responseTimeoutInMs +
                ", cancelled=" + cancelled +
                ", timedOut=" + timedOut +
                ", isDone=" + isDone +
                ", exception=" + exception +
                ", touch=" + touch +
                ", contentProcessed=" + contentProcessed +
                ", request=" + request +
                ", method=" + method +
                ", reaperFuture=" + reaperFuture +
                '}';
    }

    public void abort(Throwable t) {
        if (innerFuture != null) {
            innerFuture.cancel(true);
        }

        if (method != null) {
            method.abort();
        }

        if (reaperFuture != null) {
            reaperFuture.cancel(true);
        }

        exception.set(t);
        if (!timedOut.get() && !cancelled.get()) {
            asyncHandler.onThrowable(t);
        }     
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        cancelled.set(true);
        if (reaperFuture != null) {
            reaperFuture.cancel(true);
        }

        if (innerFuture != null) {
            return innerFuture.cancel(mayInterruptIfRunning);
        } else {
            return false;
        }
    }

    public boolean isCancelled() {
        if (innerFuture != null) {
            return innerFuture.isCancelled();
        } else {
            return false;
        }
    }

    public boolean isDone() {
        contentProcessed.set(true);
        return innerFuture.isDone();
    }

    public V get() throws InterruptedException, ExecutionException {
        try {
            return get(responseTimeoutInMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ExecutionException(e);
        }
    }

    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        V content = null;
        try {
            if (innerFuture != null) {
                content = innerFuture.get(timeout, unit);
            }
        } catch (TimeoutException t) {
            if (!contentProcessed.get() && timeout != -1 && ((System.currentTimeMillis() - touch.get()) <= responseTimeoutInMs)) {
                return get(timeout,unit);
            }
            timedOut.set(true);
            throw new TimeoutException(String.format("No response received after %s", responseTimeoutInMs));
        } catch (CancellationException ce) {
        }

        if (exception.get() != null) {
            throw new ExecutionException(exception.get());
        }
        return content;
    }

    /**
     * Is the Future still valid
     *
     * @return <code>true</code> if response has expired and should be terminated.
     */
    public boolean hasExpired(){
        return responseTimeoutInMs != -1 && ((System.currentTimeMillis() - touch.get()) >= responseTimeoutInMs);
    }

    protected void touch() {
        touch.set(System.currentTimeMillis());
    }

    public Request getRequest() {
        return request;
    }
}
