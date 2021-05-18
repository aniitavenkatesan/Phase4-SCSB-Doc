package org.recap.service.deaccession;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.HoldingsEntity;
import org.recap.model.jpa.ItemEntity;
import org.recap.repository.jpa.BibliographicDetailsRepository;
import org.recap.repository.jpa.HoldingsDetailsRepository;
import org.recap.repository.jpa.ItemDetailsRepository;
import org.recap.util.BibJSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.solr.core.SolrTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by angelind on 10/11/16.
 */
public class DeAccessSolrDocumentServiceUT extends BaseTestCaseUT {

    @Mock
    DeAccessSolrDocumentService deAccessSolrDocumentService;

    @Mock
    SolrTemplate mockedSolrTemplate;

    @Mock
    SolrInputField solrInputField;

    @Mock
    BibJSONUtil bibJSONUtil;

    @Mock
    SolrTemplate solrTemplate;

    @Mock
    BibliographicDetailsRepository mockBibliographicDetailsRepository;

    @Mock
    HoldingsDetailsRepository mockHoldingsDetailsRepository;

    @Mock
    ItemDetailsRepository mockItemDetailsRepository;

    @Value("${" + PropertyKeyConstants.SOLR_PARENT_CORE + "}")
    String solrCore;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    public DeAccessSolrDocumentService getDeAccessSolrDocumentService() {
        return deAccessSolrDocumentService;
    }

    public SolrInputField getSolrInputField() {
        return solrInputField;
    }

    public BibliographicDetailsRepository getBibliographicDetailsRepository() {
        return mockBibliographicDetailsRepository;
    }

    public HoldingsDetailsRepository getHoldingDetailRepository() {
        return mockHoldingsDetailsRepository;
    }

    public SolrTemplate getSolrTemplate() {
        return solrTemplate;
    }

    public ItemDetailsRepository getMockItemDetailsRepository() {
        return mockItemDetailsRepository;
    }

    @Test
    public void testUpdateIsDeletedBibByBibId() throws Exception {
        Integer bibId = 13;
        Random random = new Random();
        String itemBarcode = String.valueOf(random.nextInt());
        BibliographicEntity bibEntity = getBibEntityWithHoldingsAndItem(itemBarcode);
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil()).thenReturn(bibJSONUtil);
        Mockito.when(deAccessSolrDocumentService.getBibliographicDetailsRepository()).thenReturn(mockBibliographicDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getHoldingDetailRepository()).thenReturn(mockHoldingsDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getSolrTemplate()).thenReturn(solrTemplate);
        Mockito.when(deAccessSolrDocumentService.getBibliographicDetailsRepository().findById(bibId)).thenReturn(Optional.ofNullable(bibEntity));
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil().generateBibAndItemsForIndex(bibEntity, getSolrTemplate(), getBibliographicDetailsRepository(), getHoldingDetailRepository())).thenReturn(new SolrInputDocument());
        Mockito.when(deAccessSolrDocumentService.updateIsDeletedBibByBibId(Arrays.asList(bibId))).thenCallRealMethod();
        String response = deAccessSolrDocumentService.updateIsDeletedBibByBibId(Arrays.asList(bibId));
        assertNotNull(response);
        assertEquals("Bib documents updated successfully.",response);
    }

    @Test
    public void testUpdateIsDeletedBibByBibId_Exception() throws Exception {
        Integer bibId = 13;
        Random random = new Random();
        String itemBarcode = String.valueOf(random.nextInt());
        BibliographicEntity bibEntity = getBibEntityWithHoldingsAndItem(itemBarcode);
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil()).thenReturn(bibJSONUtil);
        Mockito.when(deAccessSolrDocumentService.getBibliographicDetailsRepository()).thenReturn(mockBibliographicDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getHoldingDetailRepository()).thenReturn(mockHoldingsDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getSolrTemplate()).thenReturn(solrTemplate);
        Mockito.when(deAccessSolrDocumentService.getBibliographicDetailsRepository().findById(bibId)).thenThrow(new NullPointerException());
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil().generateBibAndItemsForIndex(bibEntity, getSolrTemplate(), getBibliographicDetailsRepository(), getHoldingDetailRepository())).thenReturn(new SolrInputDocument());
        Mockito.when(deAccessSolrDocumentService.updateIsDeletedBibByBibId(Arrays.asList(bibId))).thenCallRealMethod();
        String response = deAccessSolrDocumentService.updateIsDeletedBibByBibId(Arrays.asList(bibId));
        assertNotNull(response);
        assertEquals("Bib documents failed to update.",response);
    }

    @Test
    public void testUpdateIsDeletedHoldingsByHoldingsId() throws Exception {
        Integer holdingsId = 13;
        Random random = new Random();
        String itemBarcode = String.valueOf(random.nextInt());
        BibliographicEntity bibEntity = getBibEntityWithHoldingsAndItem(itemBarcode);
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil()).thenReturn(bibJSONUtil);
        Mockito.when(deAccessSolrDocumentService.getBibliographicDetailsRepository()).thenReturn(mockBibliographicDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getHoldingDetailRepository()).thenReturn(mockHoldingsDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getSolrTemplate()).thenReturn(solrTemplate);
        Mockito.when(deAccessSolrDocumentService.getHoldingDetailRepository().findById(holdingsId)).thenReturn(Optional.ofNullable(bibEntity.getHoldingsEntities().get(0)));
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil().generateBibAndItemsForIndex(bibEntity, getSolrTemplate(), getBibliographicDetailsRepository(), getHoldingDetailRepository())).thenReturn(new SolrInputDocument());
        Mockito.when(deAccessSolrDocumentService.updateIsDeletedHoldingsByHoldingsId(Arrays.asList(holdingsId))).thenCallRealMethod();
        String response = deAccessSolrDocumentService.updateIsDeletedHoldingsByHoldingsId(Arrays.asList(holdingsId));
        assertNotNull(response);
        assertEquals("Holdings documents updated successfully.",response);
    }

    @Test
    public void testUpdateIsDeletedHoldingsByHoldingsId_Exception() throws Exception {
        Integer holdingsId = 13;
        Random random = new Random();
        String itemBarcode = String.valueOf(random.nextInt());
        BibliographicEntity bibEntity = getBibEntityWithHoldingsAndItem(itemBarcode);
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil()).thenReturn(bibJSONUtil);
        Mockito.when(deAccessSolrDocumentService.getBibliographicDetailsRepository()).thenReturn(mockBibliographicDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getHoldingDetailRepository()).thenReturn(mockHoldingsDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getSolrTemplate()).thenReturn(solrTemplate);
        Mockito.when(deAccessSolrDocumentService.getHoldingDetailRepository().findById(holdingsId)).thenThrow(new NullPointerException());
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil().generateBibAndItemsForIndex(bibEntity, getSolrTemplate(), getBibliographicDetailsRepository(), getHoldingDetailRepository())).thenReturn(new SolrInputDocument());
        Mockito.when(deAccessSolrDocumentService.updateIsDeletedHoldingsByHoldingsId(Arrays.asList(holdingsId))).thenCallRealMethod();
        String response = deAccessSolrDocumentService.updateIsDeletedHoldingsByHoldingsId(Arrays.asList(holdingsId));
        assertNotNull(response);
        assertEquals("Holdings documents failed to update.",response);
    }

    @Test
    public void testUpdateIsDeletedItemByItemIds() throws Exception {
        Integer itemId = 825172;
        Random random = new Random();
        String itemBarcode = String.valueOf(random.nextInt());
        BibliographicEntity bibliographicEntity = getBibEntityWithHoldingsAndItem(itemBarcode);
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil()).thenReturn(bibJSONUtil);
        Mockito.when(deAccessSolrDocumentService.getBibliographicDetailsRepository()).thenReturn(mockBibliographicDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getHoldingDetailRepository()).thenReturn(mockHoldingsDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getItemDetailsRepository()).thenReturn(mockItemDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getSolrTemplate()).thenReturn(solrTemplate);
        Mockito.when(deAccessSolrDocumentService.getItemDetailsRepository().findById(itemId)).thenReturn(Optional.ofNullable(bibliographicEntity.getItemEntities().get(0)));
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil().generateBibAndItemsForIndex(bibliographicEntity, getSolrTemplate(), getBibliographicDetailsRepository(), getHoldingDetailRepository())).thenReturn(new SolrInputDocument());
        Mockito.when(deAccessSolrDocumentService.updateIsDeletedItemByItemIds(Arrays.asList(itemId))).thenCallRealMethod();
        String response = deAccessSolrDocumentService.updateIsDeletedItemByItemIds(Arrays.asList(itemId));
        assertNotNull(response);
        assertEquals("Item documents updated successfully.",response);

    }

    @Test
    public void testUpdateIsDeletedItemByItemIds_Exception() throws Exception {
        Integer itemId = 825172;
        Random random = new Random();
        String itemBarcode = String.valueOf(random.nextInt());
        BibliographicEntity bibliographicEntity = getBibEntityWithHoldingsAndItem(itemBarcode);
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil()).thenReturn(bibJSONUtil);
        Mockito.when(deAccessSolrDocumentService.getBibliographicDetailsRepository()).thenReturn(mockBibliographicDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getHoldingDetailRepository()).thenReturn(mockHoldingsDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getItemDetailsRepository()).thenReturn(mockItemDetailsRepository);
        Mockito.when(deAccessSolrDocumentService.getSolrTemplate()).thenReturn(solrTemplate);
        Mockito.when(deAccessSolrDocumentService.getItemDetailsRepository().findById(itemId)).thenThrow(new NullPointerException());
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil().generateBibAndItemsForIndex(bibliographicEntity, getSolrTemplate(), getBibliographicDetailsRepository(), getHoldingDetailRepository())).thenReturn(new SolrInputDocument());
        Mockito.when(deAccessSolrDocumentService.updateIsDeletedItemByItemIds(Arrays.asList(itemId))).thenCallRealMethod();
        String response = deAccessSolrDocumentService.updateIsDeletedItemByItemIds(Arrays.asList(itemId));
        assertNotNull(response);
        assertEquals("Item documents failed to update.",response);

    }

    @Test
    public void checkGetterServices(){
        Mockito.when(deAccessSolrDocumentService.getBibJSONUtil()).thenCallRealMethod();
        Mockito.when(deAccessSolrDocumentService.getBibliographicDetailsRepository()).thenCallRealMethod();
        Mockito.when(deAccessSolrDocumentService.getHoldingDetailRepository()).thenCallRealMethod();
        Mockito.when(deAccessSolrDocumentService.getItemDetailsRepository()).thenCallRealMethod();
        Mockito.when(deAccessSolrDocumentService.getSolrTemplate()).thenCallRealMethod();
        assertNotEquals(bibJSONUtil,deAccessSolrDocumentService.getBibJSONUtil());
        assertNotEquals(solrTemplate,deAccessSolrDocumentService.getSolrTemplate());
    }


    private BibliographicEntity getBibEntityWithHoldingsAndItem(String itemBarcode) throws Exception {
        Random random = new Random();
        File bibContentFile = getBibContentFile();
        File holdingsContentFile = getHoldingsContentFile();
        String sourceBibContent = FileUtils.readFileToString(bibContentFile, "UTF-8");
        String sourceHoldingsContent = FileUtils.readFileToString(holdingsContentFile, "UTF-8");

        BibliographicEntity bibliographicEntity = new BibliographicEntity();
        bibliographicEntity.setContent(sourceBibContent.getBytes());
        bibliographicEntity.setCreatedDate(new Date());
        bibliographicEntity.setLastUpdatedDate(new Date());
        bibliographicEntity.setCreatedBy("tst");
        bibliographicEntity.setLastUpdatedBy("tst");
        bibliographicEntity.setOwningInstitutionId(1);
        String owningInstitutionBibId = String.valueOf(random.nextInt());
        bibliographicEntity.setOwningInstitutionBibId(owningInstitutionBibId);
        bibliographicEntity.setDeleted(false);

        HoldingsEntity holdingsEntity = new HoldingsEntity();
        holdingsEntity.setContent(sourceHoldingsContent.getBytes());
        holdingsEntity.setCreatedDate(new Date());
        holdingsEntity.setLastUpdatedDate(new Date());
        holdingsEntity.setCreatedBy("tst");
        holdingsEntity.setLastUpdatedBy("tst");
        holdingsEntity.setOwningInstitutionId(1);
        String owningInstitutionHoldingsId = String.valueOf(random.nextInt());
        holdingsEntity.setOwningInstitutionHoldingsId(owningInstitutionHoldingsId);
        holdingsEntity.setDeleted(false);

        ItemEntity itemEntity = new ItemEntity();
        itemEntity.setLastUpdatedDate(new Date());
        String owningInstitutionItemId = String.valueOf(random.nextInt());
        itemEntity.setOwningInstitutionItemId(owningInstitutionItemId);
        itemEntity.setOwningInstitutionId(1);
        itemEntity.setBarcode(itemBarcode);
        itemEntity.setCallNumber("x.12321");
        itemEntity.setCollectionGroupId(1);
        itemEntity.setCallNumberType("1");
        itemEntity.setCustomerCode("123");
        itemEntity.setCreatedDate(new Date());
        itemEntity.setCreatedBy("tst");
        itemEntity.setLastUpdatedBy("tst");
        itemEntity.setItemAvailabilityStatusId(1);
        itemEntity.setDeleted(false);
        itemEntity.setBibliographicEntities(Arrays.asList(bibliographicEntity));
        holdingsEntity.setItemEntities(Arrays.asList(itemEntity));
        holdingsEntity.setBibliographicEntities(Arrays.asList(bibliographicEntity));
        bibliographicEntity.setHoldingsEntities(Arrays.asList(holdingsEntity));
        bibliographicEntity.setItemEntities(Arrays.asList(itemEntity));

        return bibliographicEntity;
    }

    public void deleteByDocId(String docIdParam, String docIdValue) throws IOException, SolrServerException {
        UpdateResponse updateResponse = solrTemplate.getSolrClient().deleteByQuery(docIdParam+":"+docIdValue);
        solrTemplate.commit(solrCore);
    }

    private File getBibContentFile() throws URISyntaxException {
        URL resource = getClass().getResource("BibContent.xml");
        return new File(resource.toURI());
    }

    private File getHoldingsContentFile() throws URISyntaxException {
        URL resource = getClass().getResource("HoldingsContent.xml");
        return new File(resource.toURI());
    }

}
