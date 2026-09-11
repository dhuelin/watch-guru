package com.dhuelin.dev.watchguru.notifications.service;

import com.dhuelin.dev.watchguru.notifications.domain.DeviceToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The sender that ships until APNs and FCM credentials exist.
 *
 * <p>It logs the message and reports it delivered. That is a deliberate lie in
 * one direction only: the delivery row is written, so a later real sender will
 * not re-announce episodes the log already covers. It is the honest choice
 * against the alternative -- reporting failure and having every scan retry
 * forever against a service nobody has configured.
 *
 * <p>The log line says NOT DELIVERED in as many words, because a quiet
 * "sending push" in production would read as the feature working.
 */
@Component
@ConditionalOnProperty(name = "watch-guru.notifications.push-provider",
        havingValue = "log", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public Result send(DeviceToken device, PushMessage message) {
        log.info("NOT DELIVERED (no push credentials configured): [{}] \"{} -- {}\" to {} device {}",
                message.deepLink(),
                message.title(),
                message.body(),
                device.getPlatform(),
                abbreviate(device.getToken()));
        return Result.DELIVERED;
    }

    /** Device tokens are credentials of a sort; logs get the first characters only. */
    private static String abbreviate(String token) {
        return token.length() <= 8 ? "********" : token.substring(0, 8) + "...";
    }
}
