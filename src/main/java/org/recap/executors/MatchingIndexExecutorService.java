package org.recap.executors;

import com.google.common.collect.Lists;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jms.JmsQueueEndpoint;
import org.apache.camel.component.solr.SolrConstants;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.admin.SolrAdmin;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.repository.solr.main.BibSolrCrudRepository;
import org.recap.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Created by angelind on 30/1/17.
 */
public abstract class MatchingIndexExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(MatchingIndexExecutorService.class);

    @Autowired
    SolrAdmin solrAdmin;

    @Autowired
    ProducerTemplate producerTemplate;

    @Autowired
    InstitutionDetailsRepository institutionDetailsRepository;

    @Autowired
    BibSolrCrudRepository bibSolrCrudRepository;

    @Value("${" + PropertyKeyConstants.SOLR_SERVER_PROTOCOL + "}")
    String solrServerProtocol;

    @Value("${" + PropertyKeyConstants.SOLR_PARENT_CORE + "}")
    String solrCore;

    @Value("${" + PropertyKeyConstants.SOLR_URL + "}")
    String solrUrl;

    @Value("${" + PropertyKeyConstants.SOLR_ROUTER_URI_TYPE + "}")
    String solrRouterURI;

    @Value("${" + PropertyKeyConstants.MATCHING_ALGORITHM_INDEXING_BATCHSIZE + "}")
    Integer batchSize;

    @Value("${" + PropertyKeyConstants.MATCHING_ALGORITHM_COMMIT_INTERVAL + "}")
    Integer commitInterval;

    /**
     * This method is used for indexing the records during the matching algorithm process.
     *
     * @param operationType the operation type
     * @return the integer
     * @throws InterruptedException the interrupted exception
     */
    @Autowired
    DateUtil dateUtil;

    public Integer indexingForMatchingAlgorithm(String operationType, Date updatedDate) throws InterruptedException {
        StopWatch stopWatch1 = new StopWatch();
        stopWatch1.start();
        Integer numThreads = 5;
        Integer docsPerThread = batchSize;
        Integer commitIndexesInterval = commitInterval;
        String coreName = solrCore;
        Integer totalBibsProcessed = 0;
        Date fromDate = dateUtil.getFromDate(updatedDate);
        Date currentDate = new Date();

        try {
            ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
            Integer totalDocCount = getTotalDocCount(operationType, fromDate, currentDate);
            if(totalDocCount > 0) {
                int quotient = totalDocCount / (docsPerThread);
                int remainder = totalDocCount % (docsPerThread);
                Integer loopCount = remainder == 0 ? quotient : quotient + 1;
                logger.info("Loop Count Value : {}",loopCount);
                logger.info("Commit Indexes Interval : {}",commitIndexesInterval);
                Integer callableCountByCommitInterval = commitIndexesInterval / (docsPerThread);
                if (callableCountByCommitInterval == 0) {
                    callableCountByCommitInterval = 1;
                }
                logger.info("Number of callables to execute to commit indexes : {}",callableCountByCommitInterval);

                StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                List<Callable<Integer>> callables = new ArrayList<>();
                for (int pageNum = 0; pageNum < loopCount; pageNum++) {
                    Callable callable = getCallable(coreName, pageNum, docsPerThread, operationType, fromDate, currentDate);
                    callables.add(callable);
                }

                int futureCount = 0;
                List<List<Callable<Integer>>> partitions = Lists.partition(new ArrayList<>(callables), callableCountByCommitInterval);
                for (List<Callable<Integer>> partitionCallables : partitions) {
                    List<Future<Integer>> futures = executorService.invokeAll(partitionCallables);
                    logger.info("No of Futures Added : {}", futures.size());
                    List<Future<Integer>> collectedFutures = futures.stream().map(future -> {
                        try {
                            future.get();
                            return future;
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }).collect(Collectors.toList());
                    logger.info("No of Futures Collected : {}", collectedFutures.size());

                    int numOfBibsProcessed = 0;
                    for (Future<Integer> future : collectedFutures) {
                        try {
                            Integer entitiesCount = future.get();
                            numOfBibsProcessed += entitiesCount;
                            totalBibsProcessed += entitiesCount;
                            logger.info("Num of bibs fetched by thread : {}", entitiesCount);
                            futureCount++;
                        } catch (ExecutionException e) {
                            logger.error(ScsbCommonConstants.LOG_ERROR, e);
                        }
                    }

                    JmsQueueEndpoint solrQJmsEndPoint = (JmsQueueEndpoint) producerTemplate.getCamelContext().getEndpoint(ScsbCommonConstants.SOLR_QUEUE);
                    Integer solrQSize = solrQJmsEndPoint.getExchanges().size();
                    logger.info("Solr Queue size : {}",solrQSize);
                    while (solrQSize != 0) {
                        solrQSize = solrQJmsEndPoint.getExchanges().size();
                    }
                    Future<Object> future = producerTemplate.asyncRequestBodyAndHeader(solrRouterURI + "://" + solrUrl + "/" + coreName, "", SolrConstants.OPERATION, SolrConstants.OPERATION_COMMIT);
                    while (!future.isDone()) {
                        //NoOp.
                    }
                    logger.info("Commit future done : {}",future.isDone());

                    logger.info("Num of Bibs Processed and indexed to core{} on commit interval : {} ",coreName,numOfBibsProcessed);
                    logger.info("Total Num of Bibs Processed and indexed to core {} : {}",coreName, totalBibsProcessed);
                }
                logger.info("Total futures executed: {}",futureCount);
                stopWatch.stop();
                logger.info("Time taken to fetch {}  Bib Records and index to recap core :  {} seconds ",totalBibsProcessed,stopWatch.getTotalTimeSeconds());
                executorService.shutdown();
            } else {
                logger.info("No records found to index for the criteria");
            }
        } catch (Exception e) {
            logger.error(ScsbCommonConstants.LOG_ERROR,e);
        }
        stopWatch1.stop();
        logger.info("Total time taken: {} secs",stopWatch1.getTotalTimeSeconds());
        return totalBibsProcessed;
    }

    /**
     * This method gets the appropriate callabe which is to be processed by thread to generate solr input documents and index to solr
     *
     * @param coreName      the core name
     * @param pageNum       the page num
     * @param docsPerpage   the docs perpage
     * @param operationType the operation type
     * @return the callable
     */
    public abstract Callable getCallable(String coreName, int pageNum, int docsPerpage, String operationType, Date from, Date to);

    /**
     * This method gets total doc count based on the operation type.
     *
     * @param operationType the operation type
     * @return the total doc count
     */
    protected abstract Integer getTotalDocCount(String operationType, Date fromDate, Date toDate);
}
