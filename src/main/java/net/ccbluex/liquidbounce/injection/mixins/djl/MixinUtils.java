/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.injection.mixins.djl;

import ai.djl.util.Utils;
import net.ccbluex.liquidbounce.api.core.HttpClient;
import net.ccbluex.liquidbounce.deeplearn.DeepLearningEngine;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Map;

import static ai.djl.util.Utils.isOfflineMode;

@Pseudo
@Mixin(value = Utils.class)
public abstract class MixinUtils {

    @Unique
    private static final ThreadLocal<String> CURRENT_URL = new ThreadLocal<>();

    @Unique
    private static final OkHttpClient CLIENT = createClient();

    @Unique
    private static OkHttpClient createClient() {
        var builder = HttpClient.getClient().newBuilder();
        try {
            Class<?> progressListenerClass = Class.forName("net.ccbluex.liquidbounce.mcef.listeners.OkHttpProgressInterceptor$ProgressListener");
            Class<?> interceptorClass = Class.forName("net.ccbluex.liquidbounce.mcef.listeners.OkHttpProgressInterceptor");
            Constructor<?> constructor = interceptorClass.getConstructor(progressListenerClass);

            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                progressListenerClass.getClassLoader(),
                new Class<?>[] { progressListenerClass },
                (proxy, method, args) -> {
                    var url = CURRENT_URL.get();
                    var mainTask = DeepLearningEngine.getTask();

                    if (mainTask == null || url == null) {
                        return null;
                    }

                    var bytesRead = (Long) args[0];
                    var contentLength = (Long) args[1];
                    var done = (Boolean) args[2];

                    var task = mainTask.getOrCreateFileTask(url);
                    task.update(bytesRead, contentLength);

                    if (done) {
                        task.setCompleted(true);
                        CURRENT_URL.remove();
                    }
                    return null;
                });

            builder.addNetworkInterceptor((Interceptor) constructor.newInstance(listener));
        } catch (Exception e) {
            // MCEF not available - skip progress tracking
        }
        return builder.build();
    }

    @Inject(
            method = "openUrl(Ljava/net/URL;Ljava/util/Map;)Ljava/io/InputStream;",
            at = @At("HEAD"),
            remap = false,
            cancellable = true
    )
    private static void openUrl(URL url, Map<String, String> headers, CallbackInfoReturnable<InputStream> cir) throws IOException {
        var protocol = url.getProtocol();
        if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
            if (isOfflineMode()) {
                throw new IOException("Offline model is enabled.");
            }

            var request = new Request.Builder()
                    .url(url)
                    .headers(Headers.of(headers))
                    .build();
            CURRENT_URL.set(url.toString());
            var response = CLIENT.newCall(request).execute();
            cir.setReturnValue(response.body().byteStream());
        }
    }

}
