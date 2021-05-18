package org.recap.camel.activemq;

import org.apache.activemq.broker.jmx.DestinationViewMBean;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;

/**
 * Created by peris on 11/4/16.
 */
@Component
public class JmxHelper {

    private static final Logger logger = LoggerFactory.getLogger(JmxHelper.class);

    @Value("${" + PropertyKeyConstants.ACTIVEMQ_JMX_SERVICE_URL + "}")
    private String serviceUrl;
    private MBeanServerConnection connection;

    /**
     * This method is used to get detailed information about the queue.
     *
     * @param queueName the queue name
     * @return the bean for queue name
     */
    public DestinationViewMBean getBeanForQueueName(String queueName) {
        try {
            ObjectName nameConsumers = new ObjectName("org.apache.activemq:type=Broker,brokerName=localhost,destinationType=Queue,destinationName="+ queueName);
            return MBeanServerInvocationHandler.newProxyInstance(getConnection(), nameConsumers, DestinationViewMBean.class, true);
        } catch (MalformedObjectNameException e) {
            logger.error(ScsbCommonConstants.LOG_ERROR,e);
        }
        return null;
    }

    /**
     * Gets message bean server connection.
     *
     * @return the connection
     */
    public MBeanServerConnection getConnection() {
        if (null == connection) {
            JMXConnector connector = null;
            try {
                connector = JMXConnectorFactory.connect(new JMXServiceURL(serviceUrl));
                connection = connector.getMBeanServerConnection();
            } catch (IOException e) {
                logger.error(ScsbCommonConstants.LOG_ERROR,e);
            }
        }
        return connection;
    }
}
