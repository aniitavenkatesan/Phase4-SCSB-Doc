
package org.recap.executors;

import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.util.NamedList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.admin.SolrAdmin;
import org.recap.model.solr.SolrIndexRequest;
import org.recap.repository.jpa.BibliographicDetailsRepository;
import org.recap.repository.jpa.HoldingsDetailsRepository;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.repository.solr.main.BibSolrCrudRepository;
import org.recap.repository.solr.main.ItemCrudRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StopWatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/**
 * Created by pvsubrah on 6/14/16.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({SolrTemplate.class,SolrClient.class})
@PowerMockRunnerDelegate(SpringJUnit4ClassRunner.class)
public class ExecutorAT extends BaseTestCaseUT {

    @InjectMocks
    BibItemIndexExecutorService bibItemIndexExecutorService;

    @Mock
    ProducerTemplate producerTemplate;

    @Value("${" + PropertyKeyConstants.SOLR_SERVER_PROTOCOL + "}")
    String solrServerProtocol;

    @Value("${" + PropertyKeyConstants.SOLR_URL + "}")
    String solrUrl;

    @Value("${" + PropertyKeyConstants.SOLR_PARENT_CORE + "}")
    String solrCore;

    @Mock
    BibliographicDetailsRepository bibliographicDetailsRepository;

    @Mock
    HoldingsDetailsRepository holdingsDetailsRepository;

    @Mock
    SolrAdmin solrAdmin;

    @Mock
    BibSolrCrudRepository bibSolrCrudRepository;

    @Mock
    ItemCrudRepository itemCrudRepository;

    @Mock
    InstitutionDetailsRepository institutionDetailsRepository;

    private int numThreads = 5;
    private int docsPerThread = 10000;
    private int commitInterval = 10000;

    @Before
    public void setup()throws Exception{
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(bibItemIndexExecutorService,"solrServerProtocol",solrServerProtocol);
        ReflectionTestUtils.setField(bibItemIndexExecutorService,"solrUrl",solrUrl);
    }


    public void unloadCores() throws Exception {
        Mockito.when(solrAdmin.getCoreAdminRequest()).thenReturn(new CoreAdminRequest());
        CoreAdminRequest coreAdminRequest = solrAdmin.getCoreAdminRequest();

        coreAdminRequest.setAction(CoreAdminParams.CoreAdminAction.STATUS);
        SolrClient solrClient= PowerMockito.mock(SolrClient.class);
        long startNanos = System.nanoTime();
        SolrRequest solrRequest=new CoreAdminRequest();
        UpdateResponse res=  new UpdateResponse();
        res.setResponse(solrClient.request(solrRequest, null));
        long endNanos = System.nanoTime();
        res.setElapsedTime(TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos));
        NamedList namedList=new NamedList();
        namedList.add("test",solrRequest);
        CoreAdminResponse coreAdminResponse=new CoreAdminResponse();
        coreAdminResponse.setResponse(namedList);
        Mockito.when(coreAdminRequest.process(solrClient)).thenCallRealMethod();
        CoreAdminResponse cores = coreAdminRequest.process(solrClient);

        List<String> coreList = new ArrayList<String>();
        for (int i = 0; i < cores.getCoreStatus().size(); i++) {
            String name = cores.getCoreStatus().getName(i);
            if (name.contains("temp")) {
                coreList.add(name);
            }
        }

        for (Iterator<String> iterator = coreList.iterator(); iterator.hasNext(); ) {
            String coreName = iterator.next();
            CoreAdminResponse adminResponse = coreAdminRequest.unloadCore(coreName, true, true, solrClient);
            assertTrue(adminResponse.getStatus() == 0);

        }
    }


    @Test
    public void indexBibsAndItemsFromDB() throws Exception {
        SolrIndexRequest solrIndexRequest = new SolrIndexRequest();
        solrIndexRequest.setNumberOfThreads(numThreads);
        solrIndexRequest.setNumberOfDocs(docsPerThread);
        solrIndexRequest.setOwningInstitutionCode(null);
        solrIndexRequest.setCommitInterval(commitInterval);
        Mockito.when(bibliographicDetailsRepository.count()).thenReturn(1l);
        SolrTemplate solrTemplate = PowerMockito.mock(SolrTemplate.class);
        UpdateResponse updateResponse=new UpdateResponse();
        updateResponse.setResponse(new NamedList<>());
        Mockito.when(solrTemplate.delete(Mockito.any(),Mockito.any())).thenReturn(updateResponse);
        bibItemIndexExecutorService.index(solrIndexRequest);
        assertNotNull(solrIndexRequest);
    }

    @Test
    public void indexBibsAndItemsFromDBByOwningInstitutionId() throws Exception {
        SolrIndexRequest solrIndexRequest = new SolrIndexRequest();
        solrIndexRequest.setNumberOfThreads(numThreads);
        solrIndexRequest.setNumberOfDocs(docsPerThread);
        solrIndexRequest.setOwningInstitutionCode("CUL");
        solrIndexRequest.setCommitInterval(commitInterval);
        indexDocuments(solrIndexRequest);
        assertNotNull(solrIndexRequest);
    }


    @Test
    public void testUpdateIndexes() throws Exception {
        SolrIndexRequest solrIndexRequest = new SolrIndexRequest();
        solrIndexRequest.setNumberOfThreads(numThreads);
        solrIndexRequest.setNumberOfDocs(docsPerThread);
        solrIndexRequest.setOwningInstitutionCode("NYPL");
        solrIndexRequest.setCommitInterval(commitInterval);
        bibSolrCrudRepository.deleteAll();
        itemCrudRepository.deleteAll();
        indexDocuments(solrIndexRequest);
        long firstCount = bibSolrCrudRepository.countByDocType("Bib");
        indexDocuments(solrIndexRequest);
        long secondCount = bibSolrCrudRepository.countByDocType("Bib");
        assertEquals(firstCount, secondCount);
    }

    private void indexDocuments(SolrIndexRequest solrIndexRequest) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        bibItemIndexExecutorService.indexByOwningInstitutionId(solrIndexRequest);
        stopWatch.stop();
        System.out.println("Total time taken:" + stopWatch.getTotalTimeSeconds());
    }

    @Test
    public void testIncrementalIndex() throws Exception {
        SolrIndexRequest solrIndexRequest = new SolrIndexRequest();
        solrIndexRequest.setNumberOfThreads(numThreads);
        solrIndexRequest.setNumberOfDocs(docsPerThread);
        solrIndexRequest.setCommitInterval(commitInterval);
        solrIndexRequest.setDateFrom("27-10-2016 01:00:00");
        SimpleDateFormat dateFormatter = new SimpleDateFormat(ScsbCommonConstants.INCREMENTAL_DATE_FORMAT);
        Date from = DateUtils.addDays(new Date(), -1);
        solrIndexRequest.setDateFrom(dateFormatter.format(from));
        long dbCount = bibliographicDetailsRepository.countByLastUpdatedDateAfter(from);
        bibSolrCrudRepository.deleteAll();
        itemCrudRepository.deleteAll();
        indexDocuments(solrIndexRequest);
        SolrTemplate solrTemplate = PowerMockito.mock(SolrTemplate.class);
        solrTemplate.commit(solrCore);
        long solrCount = bibSolrCrudRepository.countByDocType("Bib");
        assertEquals(dbCount, solrCount);
    }

}

