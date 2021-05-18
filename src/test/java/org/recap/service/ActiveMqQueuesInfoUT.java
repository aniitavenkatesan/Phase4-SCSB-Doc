package org.recap.service;

import org.junit.Test;
import org.mockito.InjectMocks;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;

public class ActiveMqQueuesInfoUT extends BaseTestCaseUT {

    @InjectMocks
    ActiveMqQueuesInfo activeMqQueuesInfo;

    @Value("${" + PropertyKeyConstants.ACTIVEMQ_JOLOKIA_API_URL + "}")
    private String activeMqApiUrl;

    @Value("${" + PropertyKeyConstants.ACTIVEMQ_JOLOKIA_API_QUEUE_SIZE_ATTRIBUTE + "}")
    private String searchAttribute;

    @Value("${" + PropertyKeyConstants.ACTIVEMQ_WEB_CONSOLE_URL + "}")
    private String serviceUrl;

    @Value("${" + PropertyKeyConstants.ACTIVEMQ_CREDENTIALS + "}")
    private String activemqCredentials;

    @Test
    public void getActivemqQueuesInfo(){
        ReflectionTestUtils.setField(activeMqQueuesInfo,"serviceUrl",serviceUrl);
        ReflectionTestUtils.setField(activeMqQueuesInfo,"activemqCredentials",activemqCredentials);
        ReflectionTestUtils.setField(activeMqQueuesInfo,"activeMqApiUrl",activeMqApiUrl);
        ReflectionTestUtils.setField(activeMqQueuesInfo,"searchAttribute",searchAttribute);
        int queueSizeCount= activeMqQueuesInfo.getActivemqQueuesInfo("test");
        assertEquals(0,queueSizeCount);
    }
}
