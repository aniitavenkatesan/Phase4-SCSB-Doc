
package org.recap.executors;

import lombok.SneakyThrows;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jms.JmsQueueEndpoint;
import org.apache.camel.component.solr.SolrConstants;
import org.apache.commons.io.FileUtils;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbConstants;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.HoldingsEntity;
import org.recap.model.jpa.ItemEntity;
import org.recap.repository.jpa.BibliographicDetailsRepository;
import org.recap.util.DateUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertTrue;


/**
 * Created by angelind on 30/1/17.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest(SolrTemplate.class)
@PowerMockRunnerDelegate(SpringJUnit4ClassRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class MatchingBibItemIndexExecutorServiceUT extends BaseTestCaseUT {

    @InjectMocks
    MatchingBibItemIndexExecutorService matchingBibItemIndexExecutorService;

    @Mock
    DateUtil dateUtil;

    @Mock
    private BibliographicDetailsRepository bibliographicDetailsRepository;

    @Mock
    ProducerTemplate producerTemplate;

    @Value("${" + PropertyKeyConstants.MATCHING_ALGORITHM_INDEXING_BATCHSIZE + "}")
    Integer batchSize;

    @Value("${" + PropertyKeyConstants.MATCHING_ALGORITHM_COMMIT_INTERVAL + "}")
    Integer commitInterval;

    @Mock
    CamelContext camelContext;

    @Value("${" + PropertyKeyConstants.SOLR_PARENT_CORE + "}")
    String solrCore;

    @Value("${" + PropertyKeyConstants.SOLR_URL + "}")
    String solrUrl;

    @Value("${" + PropertyKeyConstants.SOLR_ROUTER_URI_TYPE + "}")
    String solrRouterURI;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(matchingBibItemIndexExecutorService,"batchSize",batchSize);
        ReflectionTestUtils.setField(matchingBibItemIndexExecutorService,"commitInterval",commitInterval);
        ReflectionTestUtils.setField(matchingBibItemIndexExecutorService,"solrCore",solrCore);
        ReflectionTestUtils.setField(matchingBibItemIndexExecutorService,"solrUrl",solrUrl);
        ReflectionTestUtils.setField(matchingBibItemIndexExecutorService,"solrRouterURI",solrRouterURI);
          }

    @Test
    public void indexingForMatchingAlgorithmTest() throws URISyntaxException, IOException, InterruptedException {
        Mockito.when(bibliographicDetailsRepository.getCountOfBibliographicEntitiesForChangedItems(Mockito.anyString(),Mockito.any(), Mockito.any())).thenReturn(10000l);
        Page bibliographicEntities = PowerMockito.mock(Page.class);
        Mockito.when(bibliographicDetailsRepository.getBibliographicEntitiesForChangedItems(Mockito.any(),Mockito.anyString(),Mockito.any(),Mockito.any())).thenReturn(bibliographicEntities);
        Iterator<BibliographicEntity> iterator = getBibliographicEntityIterator();
        Mockito.when(bibliographicEntities.getNumberOfElements()).thenReturn(1);
        Mockito.when(bibliographicEntities.iterator()).thenReturn(iterator);
        SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
        ReflectionTestUtils.setField(matchingBibItemIndexExecutorService,"solrTemplate",mocksolrTemplate1);
        SolrInputDocument solrInputDocument=new SolrInputDocument();
        Mockito.when(mocksolrTemplate1.convertBeanToSolrInputDocument(Mockito.any())).thenReturn(solrInputDocument);
        Mockito.when(producerTemplate.getCamelContext()).thenReturn(camelContext);
        JmsQueueEndpoint jmsQueueEndpoint=Mockito.mock(JmsQueueEndpoint.class);
        Mockito.when(camelContext.getEndpoint(Mockito.anyString())).thenReturn(jmsQueueEndpoint);
        Mockito.when(jmsQueueEndpoint.getExchanges()).thenReturn(new ArrayList<>());
        CompletableFuture<Object> future=Mockito.mock(CompletableFuture.class);
        Mockito.when(producerTemplate.asyncRequestBodyAndHeader(solrRouterURI + "://" + solrUrl + "/" + solrCore, "", SolrConstants.OPERATION, SolrConstants.OPERATION_COMMIT)).thenReturn(future);
        Mockito.when(!future.isDone()).thenReturn(true);
        Integer count = matchingBibItemIndexExecutorService.indexingForMatchingAlgorithm(ScsbConstants.INITIAL_MATCHING_OPERATION_TYPE, new Date());
        assertTrue(count > 0);
    }

    @Test
    public void indexingForMatchingAlgorithmTestEmpty() throws InterruptedException {
        Mockito.when(bibliographicDetailsRepository.getCountOfBibliographicEntitiesForChangedItems(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(0l);
        Integer count = matchingBibItemIndexExecutorService.indexingForMatchingAlgorithm(ScsbConstants.INITIAL_MATCHING_OPERATION_TYPE, new Date());
        assertTrue(count == 0);
    }

    @Test
    public void indexingForMatchingAlgorithmTestException() throws InterruptedException {
        Mockito.when(bibliographicDetailsRepository.getCountOfBibliographicEntitiesForChangedItems(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(null);
        Integer count = matchingBibItemIndexExecutorService.indexingForMatchingAlgorithm(ScsbConstants.INITIAL_MATCHING_OPERATION_TYPE, new Date());
        assertTrue(count == 0);
    }

        public BibliographicEntity getBibliographicEntity() throws URISyntaxException, IOException {
        File bibContentFile = getBibContentFile("BibContent.xml");
        String sourceBibContent = FileUtils.readFileToString(bibContentFile, "UTF-8");
        File holdingsContentFile = getBibContentFile("HoldingsContent.xml");
        String sourceHoldingsContent = FileUtils.readFileToString(holdingsContentFile, "UTF-8");

        BibliographicEntity bibliographicEntity = new BibliographicEntity();
        bibliographicEntity.setContent(sourceBibContent.getBytes());
        bibliographicEntity.setCreatedDate(new Date());
        bibliographicEntity.setLastUpdatedDate(new Date());
        bibliographicEntity.setCreatedBy("tst");
        bibliographicEntity.setLastUpdatedBy("tst");
        bibliographicEntity.setOwningInstitutionId(1);
        bibliographicEntity.setOwningInstitutionBibId("1421");
        bibliographicEntity.setId(1);
        List<BibliographicEntity> bibliographicEntitylist = new LinkedList(Arrays.asList(bibliographicEntity));


        HoldingsEntity holdingsEntity = new HoldingsEntity();
        holdingsEntity.setContent(sourceHoldingsContent.getBytes());
        holdingsEntity.setCreatedDate(new Date());
        holdingsEntity.setLastUpdatedDate(new Date());
        holdingsEntity.setCreatedBy("tst");
        holdingsEntity.setOwningInstitutionId(1);
        holdingsEntity.setLastUpdatedBy("tst");
        holdingsEntity.setOwningInstitutionHoldingsId("1621");
        holdingsEntity.setId(1);
        List<HoldingsEntity> holdingsEntitylist = new LinkedList(Arrays.asList(holdingsEntity));

        ItemEntity itemEntity = new ItemEntity();
        itemEntity.setLastUpdatedDate(new Date());
        itemEntity.setOwningInstitutionItemId("6320902");
        itemEntity.setOwningInstitutionId(1);
        itemEntity.setBarcode("32101086866140");
        itemEntity.setCallNumber("x.12321");
        itemEntity.setCollectionGroupId(1);
        itemEntity.setCallNumberType("1");
        itemEntity.setCustomerCode("PA");
        itemEntity.setCreatedDate(new Date());
        itemEntity.setCreatedBy("tst");
        itemEntity.setLastUpdatedBy("tst");
        itemEntity.setItemAvailabilityStatusId(1);
        itemEntity.setId(1);
        List<ItemEntity> itemEntitylist = new LinkedList(Arrays.asList(itemEntity));

        holdingsEntity.setBibliographicEntities(bibliographicEntitylist);
        holdingsEntity.setItemEntities(itemEntitylist);
        bibliographicEntity.setHoldingsEntities(holdingsEntitylist);
        bibliographicEntity.setItemEntities(itemEntitylist);
        itemEntity.setHoldingsEntities(holdingsEntitylist);
        itemEntity.setBibliographicEntities(bibliographicEntitylist);
        return bibliographicEntity;
    }


    public File getBibContentFile(String xml) throws URISyntaxException {
        URL resource = getClass().getResource(xml);
        return new File(resource.toURI());
    }

    private Iterator<BibliographicEntity> getBibliographicEntityIterator() {
        Iterator<BibliographicEntity> iterator=new Iterator<BibliographicEntity>() {
            int count;

            @Override
            public boolean hasNext() {
                count ++;
                if(count==1){
                    return true;}
                else {
                    return false;
                }
            }

            @SneakyThrows
            @Override
            public BibliographicEntity next() {
                return getBibliographicEntity();
            }
        };
        return iterator;
    }

}
