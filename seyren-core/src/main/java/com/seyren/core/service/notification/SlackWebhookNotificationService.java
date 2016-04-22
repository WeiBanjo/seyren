/**
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
package com.seyren.core.service.notification;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.seyren.core.domain.Alert;
import com.seyren.core.domain.AlertType;
import com.seyren.core.domain.Check;
import com.seyren.core.domain.Subscription;
import com.seyren.core.domain.SubscriptionType;
import com.seyren.core.exception.NotificationFailedException;
import com.seyren.core.util.config.SeyrenConfig;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
public class SlackWebhookNotificationService implements NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackWebhookNotificationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String COLOR_ERROR = "#d93240";
    private static final String COLOR_WARN = "#FFD801";
    private static final String COLOR_OK = "#5bb12f";
    private static final String COLOR_UNKNOWN = "#CACACA";

    private final SeyrenConfig seyrenConfig;
    private final String baseUrl;
    private final String slackUsername;

    @Inject
    public SlackWebhookNotificationService(SeyrenConfig seyrenConfig) {
        this.seyrenConfig = seyrenConfig;
        this.slackUsername = seyrenConfig.getSlackUsername();
        this.baseUrl = seyrenConfig.getSlackWebhookUrl();

    }

    protected SlackWebhookNotificationService(SeyrenConfig seyrenConfig, String baseUrl) {
        this.seyrenConfig = seyrenConfig;
        this.slackUsername = seyrenConfig.getSlackUsername();
        this.baseUrl = baseUrl;
    }

    @Override
    public void sendNotification(Check check, Subscription subscription, List<Alert> alerts) throws NotificationFailedException {
        String targetSlackChannel = subscription.getTarget();
        String url = this.baseUrl;
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(url);
        post.addHeader("accept", "application/json");

        Map<String, Object> body = Maps.newHashMap();
        body.put("channel", targetSlackChannel);
        body.put("username", this.slackUsername);
        body.put("icon_emoji", ":seyren:");

        List<Map<String, Object>> attachments = Lists.newArrayList();
        body.put("attachments", attachments);

        formatContent(check, alerts, attachments);

        try {
            HttpEntity entity = new StringEntity(MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON);
            post.setEntity(entity);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.info("> parameters: {}", MAPPER.writeValueAsBytes(body));
            }
            HttpResponse response = client.execute(post);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Status: {}, Body: {}", response.getStatusLine(), new BasicResponseHandler().handleResponse(response));
            }
        } catch (Exception e) {
            LOGGER.warn("Error posting to Slack", e);
        } finally {
            post.releaseConnection();
            HttpClientUtils.closeQuietly(client);
        }
    }

    private void formatContent(Check check, List<Alert> alerts, List<Map<String, Object>> attachments) {
        AlertType state = check.getState();
        String checkUrl = String.format("%s/#/checks/%s", seyrenConfig.getBaseUrl(), check.getId());

        Map<String, Object> attachment = Maps.newHashMap();
        List<Map<String, Object>> fields = Lists.newArrayList();
        attachment.put("color", getAlertColor(state));
        attachment.put("fallback", check.getName());
        attachment.put("title", check.getName());
        attachment.put("title_link", checkUrl);
        attachment.put("fields", fields);

        Alert alert = alerts.get(alerts.size() - 1);
        AlertType toType = alert.getToType();
        AlertType fromType = alert.getFromType();

        Map<String, Object> newStateField = getStateField("New State Value", toType.toString());
        fields.add(newStateField);

        Map<String, Object> oldStateField = getStateField("Old State Value", fromType.toString());
        fields.add(oldStateField);

        Map<String, Object> descriptionField = getDescription(alert);
        fields.add(descriptionField);
        attachments.add(attachment);
    }

    private Map<String, Object> getDescription(Alert alert) {
        Map<String, Object> descriptionField = Maps.newHashMap();
        descriptionField.put("title", "Description");
        descriptionField.put("value", String.format("%s = %s", alert.getTarget(), alert.getValue().toString()));
        return descriptionField;
    }

    private Map<String, Object> getStateField(String title, String value) {
        Map<String, Object> newStateField = Maps.newHashMap();
        newStateField.put("title", title);
        newStateField.put("value", value);
        newStateField.put("short", true);
        return newStateField;
    }

    private String getAlertColor(AlertType state) {
        return state == AlertType.ERROR ? COLOR_ERROR : state == AlertType.OK ? COLOR_OK : state == AlertType.WARN ? COLOR_WARN : COLOR_UNKNOWN;
    }

    @Override
    public boolean canHandle(SubscriptionType subscriptionType) {
        return subscriptionType == SubscriptionType.SLACKWEBHOOK;
    }

}
