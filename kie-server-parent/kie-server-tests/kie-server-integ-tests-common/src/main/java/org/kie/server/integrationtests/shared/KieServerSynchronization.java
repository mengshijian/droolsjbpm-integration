/*
 * Copyright 2016 JBoss by Red Hat.
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
package org.kie.server.integrationtests.shared;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import org.kie.api.executor.STATUS;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.KieContainerResourceList;
import org.kie.server.api.model.KieContainerStatus;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.RequestInfoInstance;
import org.kie.server.client.JobServicesClient;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;

public class KieServerSynchronization {

    private static final long SERVICE_TIMEOUT = 30000;
    private static final long TIMEOUT_BETWEEN_CALLS = 200;

    public static void waitForJobToFinish(final JobServicesClient jobServicesClient, final Long jobId) throws Exception {
        waitForCondition(() -> {
            RequestInfoInstance result = jobServicesClient.getRequestById(jobId, false, false);

            // If job finished (to one of final states) then return.
            if(STATUS.CANCELLED.toString().equals(result.getStatus()) ||
                    STATUS.DONE.toString().equals(result.getStatus()) ||
                    STATUS.ERROR.toString().equals(result.getStatus())) {
                return true;
            }
            return false;
        });
    }

    public static void waitForKieServerSynchronization(final KieServicesClient client, final int numberOfExpectedContainers) throws Exception {
        waitForCondition(() -> {
            ServiceResponse<KieContainerResourceList> containersList = client.listContainers();

            // If synchronization finished (number of containers same as expected) then return.
            if (containersList.getResult().getContainers() == null) {
                if (numberOfExpectedContainers == 0) {
                    return true;
                }
            } else if (numberOfExpectedContainers == containersList.getResult().getContainers().size()) {
                // Check that all containers are created or disposed.
                boolean containersInitializing = false;
                for (KieContainerResource container : containersList.getResult().getContainers()) {
                    if (KieContainerStatus.CREATING.equals(container.getStatus()) ||
                            KieContainerStatus.DISPOSING.equals(container.getStatus())) {
                        containersInitializing = true;
                    }
                }
                if (!containersInitializing) {
                    return true;
                }
            }
            return false;
        });
    }

    public static void waitForProcessInstanceToFinish(final ProcessServicesClient processClient, final String containerId, final long processInstanceId) throws Exception {
        waitForCondition(() -> {
            ProcessInstance processInstance = processClient.getProcessInstance(containerId, processInstanceId);

            // If process instance is finished (to one of final states) then return.
            if (((Integer) org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED).equals(processInstance.getState()) || ((Integer) org.kie.api.runtime.process.ProcessInstance.STATE_ABORTED).equals(processInstance.getState())) {
                return true;
            }
            return false;
        });
    }

    public static void waitForProcessInstanceStart(final QueryServicesClient queryClient, final String containerId) throws Exception {
        waitForCondition(() -> {
            List<ProcessInstance> processInstances = queryClient.findProcessInstances(0, 100);

            if (processInstances.size() == 1) {
                return true;
            }
            return false;
        });
    }

    /**
     * @param condition Condition result supplier. If returns true then condition is met.
     * @throws Exception
     */
    private static void waitForCondition(BooleanSupplier condition) throws Exception {
        long timeoutTime = Calendar.getInstance().getTimeInMillis() + SERVICE_TIMEOUT;
        while (Calendar.getInstance().getTimeInMillis() < timeoutTime) {

            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(TIMEOUT_BETWEEN_CALLS);
        }
        throw new TimeoutException("Synchronization failed for defined timeout: " + SERVICE_TIMEOUT + " milliseconds.");
    }
}
