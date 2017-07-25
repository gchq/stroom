/*
 * Copyright 2017 Crown Copyright
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

package stroom.policy.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.XMLMarshallerUtil;
import stroom.policy.shared.FindPolicyCriteria;
import stroom.policy.shared.Policy;
import stroom.policy.shared.PolicyService;
import stroom.policy.shared.DataRetentionPolicy;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataRetentionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataRetentionService.class);

    private static final String POLICY_NAME = "Data Retention";

    private final PolicyService policyService;


    @Inject
    DataRetentionService(final PolicyService policyService) {
        this.policyService = policyService;
    }

    public DataRetentionPolicy load() {
        final Policy policy = getPolicy();
        if (policy == null) {
            throw new RuntimeException("Unable to fetch or create policy in DB");
        }

        return read(policy);
    }

    public DataRetentionPolicy save(final DataRetentionPolicy dataRetentionPolicy) {
        Policy policy = getPolicy();
        if (policy == null) {
            throw new RuntimeException("Unable to fetch or create policy in DB");
        }

        if (policy.getVersion() != dataRetentionPolicy.getVersion()) {
            throw new RuntimeException("The policy has been updated by somebody else");
        }

        try {
            String data = marshal(dataRetentionPolicy);
            policy.setData(data);
            policy = policyService.save(policy);

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }

        return read(policy);
    }

    private DataRetentionPolicy read(final Policy policy) {
        try {
            String data = policy.getData();
            DataRetentionPolicy dataRetentionPolicy = unmarshal(data);
            if (dataRetentionPolicy == null) {
                dataRetentionPolicy = new DataRetentionPolicy(new ArrayList<>());
            }

            dataRetentionPolicy.setVersion(policy.getVersion());
            return dataRetentionPolicy;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }
    }

    private Policy getPolicy() {
        List<Policy> policyList = policyService.find(new FindPolicyCriteria(POLICY_NAME));

        Policy policy = null;
        if (policyList == null || policyList.size() == 0) {
            try {
                policy = new Policy();
                policy.setName(POLICY_NAME);
                policy = policyService.save(policy);
            } catch (final Exception e) {
                LOGGER.debug(e.getMessage(), e);
                // Try and fetch again.
                policyList = policyService.find(new FindPolicyCriteria(POLICY_NAME));
                if (policyList != null && policyList.size() == 1) {
                    policy = policyList.get(0);
                }
            }
        } else {
            policy = policyList.get(0);
        }

        return policy;
    }

    private DataRetentionPolicy unmarshal(final String data) throws Exception {
        final JAXBContext context = JAXBContext.newInstance(DataRetentionPolicy.class);
        return XMLMarshallerUtil.unmarshal(context, DataRetentionPolicy.class, data);
    }

    private String marshal(final DataRetentionPolicy dataRetentionPolicy) throws Exception {
        final JAXBContext context = JAXBContext.newInstance(DataRetentionPolicy.class);
        return XMLMarshallerUtil.marshal(context, dataRetentionPolicy);
    }
}
