package org.recap.executors;

import lombok.SneakyThrows;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.junit.Test;
import org.junit.Before;
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
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.admin.SolrAdmin;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.HoldingsEntity;
import org.recap.model.jpa.InstitutionEntity;
import org.recap.model.jpa.ItemEntity;
import org.recap.model.solr.SolrIndexRequest;
import org.recap.repository.jpa.BibliographicDetailsRepository;
import org.recap.repository.jpa.HoldingsDetailsRepository;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.repository.solr.main.BibSolrCrudRepository;
import org.recap.repository.solr.temp.BibCrudRepositoryMultiCoreSupport;
import org.recap.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Created by premkb on 29/7/16.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest(SolrTemplate.class)
@PowerMockRunnerDelegate(SpringJUnit4ClassRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class BibItemIndexExecutorServiceUT extends BaseTestCaseUT {

    private static final Logger logger = LoggerFactory.getLogger(BibItemIndexExecutorServiceUT.class);

    @InjectMocks
    BibItemIndexExecutorService bibItemIndexExecutorService;

    @Mock
    BibliographicDetailsRepository mockBibliographicDetailsRepository;

    @Mock
    SolrAdmin mockSolrAdmin;

    @Mock
    BibItemIndexCallable mockBibItemIndexCallable;

    @Mock
    BibCrudRepositoryMultiCoreSupport mockBibCrudRepositoryMultiCoreSupport;

    @Mock
    InstitutionDetailsRepository institutionDetailsRepository;

    @Mock
    BibSolrCrudRepository bibSolrCrudRepository;

    @Mock
    ProducerTemplate producerTemplate;

    @Mock
    HoldingsDetailsRepository holdingsDetailsRepository;

    @Mock
    CamelContext camelContext;

    @Mock
    SolrAdmin solrAdmin;

    @Mock
    DateUtil dateUtil;

    @Value("${" + PropertyKeyConstants.SOLR_ROUTER_URI_TYPE + "}")
    String solrRouterURI;

    @Value("${" + PropertyKeyConstants.SOLR_SERVER_PROTOCOL + "}")
    String solrServerProtocol;

    @Value("${" + PropertyKeyConstants.SOLR_URL + "}")
    String solrUrl;

    @Value("${" + PropertyKeyConstants.SOLR_PARENT_CORE + "}")
    String solrCore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(bibItemIndexExecutorService,"solrServerProtocol",solrServerProtocol);
        ReflectionTestUtils.setField(bibItemIndexExecutorService,"solrUrl",solrUrl);
        ReflectionTestUtils.setField(bibItemIndexExecutorService,"solrCore",solrCore);
        ReflectionTestUtils.setField(bibItemIndexExecutorService,"solrRouterURI",solrRouterURI);
    }

    @Test
    public void getBibCrudRepositoryMultiCoreSupport() throws Exception {
        BibCrudRepositoryMultiCoreSupport bibCrudRepositoryMultiCoreSupport= bibItemIndexExecutorService.getBibCrudRepositoryMultiCoreSupport(solrUrl,solrCore);
        assertNotNull(bibCrudRepositoryMultiCoreSupport);
    }

    @Test
    public void mergeIndexFrequency() throws Exception {

        Mockito.when(mockBibliographicDetailsRepository.countByIsDeletedFalse()).thenReturn(500000L);
        Mockito.when(mockBibItemIndexCallable.call()).thenReturn(1000);

        bibItemIndexExecutorService.setBibliographicDetailsRepository(mockBibliographicDetailsRepository);
        bibItemIndexExecutorService.setSolrAdmin(mockSolrAdmin);
        SolrIndexRequest solrIndexRequest = new SolrIndexRequest();
        solrIndexRequest.setNumberOfThreads(5);
        solrIndexRequest.setNumberOfDocs(1);
        solrIndexRequest.setOwningInstitutionCode("PUL");
        solrIndexRequest.setCommitInterval(0);
        SimpleDateFormat dateFormatter = new SimpleDateFormat(ScsbCommonConstants.INCREMENTAL_DATE_FORMAT);
        Date from = DateUtils.addDays(new Date(), -1);
        solrIndexRequest.setDateFrom(dateFormatter.format(from));
        InstitutionEntity institutionEntity=new InstitutionEntity();
        institutionEntity.setId(1);
        institutionEntity.setInstitutionCode("PUL");
        institutionEntity.setInstitutionName("Princeton");
        Mockito.when(institutionDetailsRepository.findByInstitutionCode(Mockito.anyString())).thenReturn(institutionEntity);
        Mockito.when(mockBibliographicDetailsRepository.countByOwningInstitutionIdAndLastUpdatedDateAfter(Mockito.anyInt(),Mockito.any())).thenReturn(1l);
        Page bibliographicEntities = PowerMockito.mock(Page.class);
        SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
        ReflectionTestUtils.setField(bibItemIndexExecutorService,"solrTemplate",mocksolrTemplate1);
        Mockito.when(bibliographicEntities.getNumberOfElements()).thenReturn(1);
        Mockito.when(bibliographicEntities.iterator()).thenReturn(getBibliographicEntityIterator());
        Mockito.when(mockBibliographicDetailsRepository.findByOwningInstitutionIdAndLastUpdatedDateAfter(Mockito.any(),Mockito.anyInt(),Mockito.any())).thenReturn(bibliographicEntities);
        Mockito.when( bibSolrCrudRepository.countByDocType(Mockito.anyString())).thenReturn(1l);
        Mockito.when(mocksolrTemplate1.convertBeanToSolrInputDocument(Mockito.any())).thenReturn(getSolrInputFields());
        bibItemIndexExecutorService.index(solrIndexRequest);
        assertNotNull(solrIndexRequest);
    }

    @Test
    public void partialIndexBibIdList() throws Exception {
            Page bibliographicEntities = PowerMockito.mock(Page.class);
            Mockito.when(bibliographicEntities.getNumberOfElements()).thenReturn(1);
            Mockito.when(bibliographicEntities.iterator()).thenReturn(getBibliographicEntityIterator());
            Mockito.when(mockBibliographicDetailsRepository.findByOwningInstitutionIdAndLastUpdatedDateAfter(Mockito.any(), Mockito.anyInt(), Mockito.any())).thenReturn(bibliographicEntities);
            Mockito.when(bibSolrCrudRepository.countByDocType(Mockito.anyString())).thenReturn(1l);
            SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
            ReflectionTestUtils.setField(bibItemIndexExecutorService, "solrTemplate", mocksolrTemplate1);
            Mockito.when(mocksolrTemplate1.convertBeanToSolrInputDocument(Mockito.any())).thenReturn(getSolrInputFields());
            Mockito.when(mockBibliographicDetailsRepository.getCountOfBibBasedOnBibIds(Mockito.anyList())).thenReturn(1l);
            Mockito.when(mockBibliographicDetailsRepository.getBibsBasedOnBibIds(Mockito.any(), Mockito.anyList())).thenReturn(bibliographicEntities);
            int count = bibItemIndexExecutorService.partialIndex(getSolrIndexRequest(ScsbConstants.BIB_ID_LIST));
            assertEquals(0, count);
    }

    @Test
    public void partialIndexBibIdRange() throws Exception {
            Page bibliographicEntities = PowerMockito.mock(Page.class);
            SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
            ReflectionTestUtils.setField(bibItemIndexExecutorService, "solrTemplate", mocksolrTemplate1);
            Mockito.when(bibliographicEntities.getNumberOfElements()).thenReturn(1);
            Mockito.when(bibliographicEntities.iterator()).thenReturn(getBibliographicEntityIterator());
            Mockito.when(mockBibliographicDetailsRepository.findByOwningInstitutionIdAndLastUpdatedDateAfter(Mockito.any(), Mockito.anyInt(), Mockito.any())).thenReturn(bibliographicEntities);
            Mockito.when(bibSolrCrudRepository.countByDocType(Mockito.anyString())).thenReturn(1l);
            Mockito.when(mocksolrTemplate1.convertBeanToSolrInputDocument(Mockito.any())).thenReturn(getSolrInputFields());
            Mockito.when(mockBibliographicDetailsRepository.getCountOfBibBasedOnBibIdRange(Mockito.anyInt(),Mockito.anyInt())).thenReturn(1l);
            Mockito.when(mockBibliographicDetailsRepository.getBibsBasedOnBibIdRange(Mockito.any(),Mockito.anyInt(),Mockito.anyInt())).thenReturn(bibliographicEntities);
            int count = bibItemIndexExecutorService.partialIndex(getSolrIndexRequest(ScsbConstants.BIB_ID_RANGE));
            assertEquals(0, count);
    }

    @Test
    public void partialIndexDateRange() throws Exception {
            Page bibliographicEntities = PowerMockito.mock(Page.class);
            SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
            ReflectionTestUtils.setField(bibItemIndexExecutorService, "solrTemplate", mocksolrTemplate1);
            Mockito.when(bibliographicEntities.getNumberOfElements()).thenReturn(1);
            Mockito.when(bibliographicEntities.iterator()).thenReturn(getBibliographicEntityIterator());
            Mockito.when(mockBibliographicDetailsRepository.findByOwningInstitutionIdAndLastUpdatedDateAfter(Mockito.any(), Mockito.anyInt(), Mockito.any())).thenReturn(bibliographicEntities);
            Mockito.when(bibSolrCrudRepository.countByDocType(Mockito.anyString())).thenReturn(1l);
            Mockito.when(mocksolrTemplate1.convertBeanToSolrInputDocument(Mockito.any())).thenReturn(getSolrInputFields());
            Mockito.when(mockBibliographicDetailsRepository.getCountOfBibBasedOnDateRange(Mockito.any(),Mockito.any())).thenReturn(1l);
            Mockito.when(mockBibliographicDetailsRepository.getBibsBasedOnDateRange(Mockito.any(),Mockito.any(),Mockito.any())).thenReturn(bibliographicEntities);
            Mockito.when(dateUtil.getFromDate(Mockito.any())).thenCallRealMethod();
            Mockito.when(dateUtil.getToDate(Mockito.any())).thenCallRealMethod();
            int count = bibItemIndexExecutorService.partialIndex(getSolrIndexRequest(ScsbConstants.DATE_RANGE));
            assertEquals(0, count);
    }

    @Test
    public void partialIndexForEmptyValue() throws Exception {
        SolrIndexRequest solrIndexRequest=new SolrIndexRequest();
        int countExp = bibItemIndexExecutorService.partialIndex(solrIndexRequest);
        solrIndexRequest.setNumberOfThreads(5);
        int count = bibItemIndexExecutorService.partialIndex(solrIndexRequest);
        assertEquals(countExp, count);
    }

    @Test
    public void partialIndexException() throws Exception {
        SolrIndexRequest solrIndexRequest = getSolrIndexRequest(ScsbConstants.DATE_RANGE) ;
        solrIndexRequest.setDateFrom("01-10-2020 00:00");
        solrIndexRequest.setDateTo("02-10-2020 00:00");
        solrIndexRequest.setCommitInterval(0);
        Page bibliographicEntities = PowerMockito.mock(Page.class);
        SolrTemplate mocksolrTemplate1 = PowerMockito.mock(SolrTemplate.class);
        ReflectionTestUtils.setField(bibItemIndexExecutorService, "solrTemplate", mocksolrTemplate1);
        Mockito.when(bibliographicEntities.getNumberOfElements()).thenReturn(1);
        Mockito.when(bibliographicEntities.iterator()).thenReturn(getIterator());
        Mockito.when(mockBibliographicDetailsRepository.findByOwningInstitutionIdAndLastUpdatedDateAfter(Mockito.any(), Mockito.anyInt(), Mockito.any())).thenReturn(bibliographicEntities);
        Mockito.when(bibSolrCrudRepository.countByDocType(Mockito.anyString())).thenReturn(1l);
        Mockito.when(mocksolrTemplate1.convertBeanToSolrInputDocument(Mockito.any())).thenReturn(getSolrInputFields());
        Mockito.when(mockBibliographicDetailsRepository.getCountOfBibBasedOnDateRange(Mockito.any(),Mockito.any())).thenReturn(1l);
        Mockito.when(mockBibliographicDetailsRepository.getBibsBasedOnDateRange(Mockito.any(),Mockito.any(),Mockito.any())).thenReturn(bibliographicEntities);
        Mockito.when(dateUtil.getFromDate(Mockito.any())).thenCallRealMethod();
        Mockito.when(dateUtil.getToDate(Mockito.any())).thenCallRealMethod();
        int count = bibItemIndexExecutorService.partialIndex(solrIndexRequest);
        assertEquals(0, count);
    }

    @Test
    public void indexBibsAndItemsFromDB() throws Exception {
        SolrIndexRequest solrIndexRequest = new SolrIndexRequest();
        solrIndexRequest.setNumberOfThreads(5);
        solrIndexRequest.setNumberOfDocs(10000);
        solrIndexRequest.setOwningInstitutionCode(null);
        solrIndexRequest.setCommitInterval(10000);
        Mockito.when(mockBibliographicDetailsRepository.count()).thenReturn(1l);
        SolrTemplate solrTemplate = PowerMockito.mock(SolrTemplate.class);
        UpdateResponse updateResponse=new UpdateResponse();
        updateResponse.setResponse(new NamedList<>());
        Mockito.when(solrTemplate.delete(Mockito.any(),Mockito.any())).thenReturn(updateResponse);
        Mockito.when(solrAdmin.getCoresStatus()).thenReturn(1).thenReturn(0);
        bibItemIndexExecutorService.index(solrIndexRequest);
        assertNotNull(solrIndexRequest);
    }

    private SolrIndexRequest getSolrIndexRequest(String partialIndexType) {
        SolrIndexRequest solrIndexRequest = new SolrIndexRequest();
        solrIndexRequest.setNumberOfThreads(5);
        solrIndexRequest.setNumberOfDocs(1);
        solrIndexRequest.setPartialIndexType(partialIndexType);
        solrIndexRequest.setBibIds("1,2");
        solrIndexRequest.setCommitInterval(1);
        solrIndexRequest.setFromBibId("1");
        solrIndexRequest.setToBibId("2");
        return solrIndexRequest;
    }

    @Test
    public void getTotalDocCount() throws Exception {
        Mockito.when(mockBibliographicDetailsRepository.countByOwningInstitutionId(Mockito.anyInt())).thenReturn(2l);
        int count= bibItemIndexExecutorService.getTotalDocCount(1,null);
        assertEquals(2,count);
    }


    private SolrInputDocument getSolrInputFields() {
        SolrInputDocument solrInputDocument = new SolrInputDocument();
        solrInputDocument.setField("id", "123");
        solrInputDocument.setField("Title_search", "Title1");
        solrInputDocument.setField("Author_search", "Author1");
        return solrInputDocument;
    }

    private Iterator<BibliographicEntity> getBibliographicEntityIterator() {
        return new Iterator<BibliographicEntity>() {

            @Override
            public boolean hasNext() {
                return false;
            }

            @SneakyThrows
            @Override
            public BibliographicEntity next() {
                return getBibliographicEntity();
            }
        };
    }

    private Iterator<BibliographicEntity> getIterator() {
        return new Iterator<BibliographicEntity>() {
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
}
