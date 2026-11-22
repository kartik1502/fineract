/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.integrationtests.client.feign.tests;

import static org.apache.fineract.integrationtests.client.feign.modules.ClientRequestBuilders.createActivePersonClient;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.feign.ObjectMapperFactory;
import org.apache.fineract.client.models.PostClientsClientIdChargesRequest;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.integrationtests.client.FeignIntegrationTest;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignChargesHelper;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignClientHelper;
import org.apache.fineract.integrationtests.client.feign.modules.ChargeRequestBuilders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class FeignClientsLocalDateLocaleTest extends FeignIntegrationTest {

    public static final String ACTIVATION_DATE = "21 November 2026";
    private FeignClientHelper clientHelper;
    private FeignChargesHelper chargesHelper;
    private static final BigDecimal CLIENT_CHARGE_AMOUNT = BigDecimal.valueOf(200);

    @BeforeAll
    void setupHelpers() {
        clientHelper = new FeignClientHelper(fineractClient());
        chargesHelper = new FeignChargesHelper(fineractClient());
    }

    @Test
    void testRetrieveClientLocaleDate() {

        PostClientsRequest request = createActivePersonClient("21 November 2026");
        request.submittedOnDate("11 November 2026");
        final PostClientsResponse client = clientHelper.createClient(request);
        final Long clientID = client.getClientId();

        try (Response r = fineractClient().create(ClientApiForTests.class).getClient(String.valueOf(clientID))) {
            JsonNode jsonNode = readTree(r);
            JsonNode activationDate = jsonNode.get("activationDate");
            assertDate(activationDate, "2026", "11", "21");

            JsonNode submittedDate = jsonNode.get("timeline").get("submittedOnDate");
            assertDate(submittedDate, "2026", "11", "11");

            JsonNode activatedOnDate = jsonNode.get("timeline").get("activatedOnDate");
            assertDate(activatedOnDate, "2026", "11", "21");
        }
    }

    @Test
    void testRetrieveClientTransactionLocaleDate() {

        PostClientsRequest request = createActivePersonClient(ACTIVATION_DATE);
        final PostClientsResponse client = clientHelper.createClient(request);
        final Long clientID = client.getClientId();

        final Long chargeId = chargesHelper.createClientSpecifiedDueDateCharge(20).getResourceId();
        final Long clientChargeId = chargesHelper.addClientCharge(clientID, clientCharge(chargeId, ACTIVATION_DATE)).getResourceId();
        final Long clientTransactionId = chargesHelper
                .payClientCharge(clientID, clientChargeId, ChargeRequestBuilders.payClientCharge(ACTIVATION_DATE, 10)).getTransactionId();

        try (Response r = fineractClient().create(ClientApiForTests.class).getClientTransaction(String.valueOf(clientID),
                String.valueOf(clientTransactionId))) {

            JsonNode jsonNode = readTree(r);
            assertDate(jsonNode.get("date"), "2026", "11", "21");
            assertDate(jsonNode.get("submittedOnDate"), "2026", "11", "22");
        }

    }

    private void assertDate(JsonNode activationDate, String year, String month, String day) {
        assertThat(activationDate.isArray()).isTrue();
        assertThat(activationDate).hasSize(3);
        assertThat(activationDate.get(0).asText()).isEqualTo(year);
        assertThat(activationDate.get(1).asText()).isEqualTo(month);
        assertThat(activationDate.get(2).asText()).isEqualTo(day);
    }

    private static JsonNode readTree(final Response response) {
        assertEquals(200, response.status());
        try {
            return ObjectMapperFactory.getShared().readTree(response.body().asInputStream());
        } catch (final Exception e) {
            throw new AssertionError("Unreadable response: " + response.body().toString(), e);
        }
    }

    private static PostClientsClientIdChargesRequest clientCharge(Long chargeId, String chargeDueDate) {
        return ChargeRequestBuilders.clientCharge(chargeId, chargeDueDate, CLIENT_CHARGE_AMOUNT);
    }

    interface ClientApiForTests {

        @RequestLine("GET /v1/clients/{clientId}")
        Response getClient(@Param("clientId") String clientId);

        @RequestLine("GET /v1/clients/{clientId}/transactions/{transactionId}")
        Response getClientTransaction(@Param("clientId") String clientId, @Param("transactionId") String transactionId);
    }
}
