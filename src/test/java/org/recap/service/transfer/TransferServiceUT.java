package org.recap.service.transfer;

import org.apache.camel.ProducerTemplate;
import org.apache.solr.client.solrj.SolrServerException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.recap.BaseTestCaseUT;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.HoldingsEntity;
import org.recap.model.jpa.InstitutionEntity;
import org.recap.model.jpa.ItemEntity;
import org.recap.model.transfer.Destination;
import org.recap.model.transfer.HoldingTransferResponse;
import org.recap.model.transfer.HoldingsTransferRequest;
import org.recap.model.transfer.ItemDestination;
import org.recap.model.transfer.ItemSource;
import org.recap.model.transfer.ItemTransferRequest;
import org.recap.model.transfer.ItemTransferResponse;
import org.recap.model.transfer.Source;
import org.recap.model.transfer.TransferRequest;
import org.recap.repository.jpa.BibliographicDetailsRepository;
import org.recap.repository.jpa.HoldingsDetailsRepository;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.service.accession.AccessionDAO;
import org.recap.service.accession.DummyDataService;
import org.recap.service.accession.SolrIndexService;
import org.recap.util.HelperUtil;
import org.recap.util.MarcUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by Anithav on 30/06/20.
 */

public class TransferServiceUT extends BaseTestCaseUT {
    private static final Logger logger = LoggerFactory.getLogger(TransferServiceUT.class);

    @InjectMocks
    TransferService mockTransferService;

    @Mock
    InstitutionDetailsRepository institutionDetailsRepository;

    @Mock
    BibliographicDetailsRepository bibliographicDetailsRepository;

    @Mock
    HoldingsDetailsRepository holdingsDetailsRepository;

    @Mock
    MarcUtil marcUtil;

    @Mock
    HelperUtil helperUtil;

    @Value("${" + PropertyKeyConstants.TRANSFER_API_NONHOLDINGID_INSTITUTION + "}")
    String nonHoldingIdInstitutionForTransferApi;

    @Mock
    DummyDataService dummyDataService;

    @Mock
    AccessionDAO accessionDAO;

    @Mock
    SolrIndexService solrIndexService;

    @Mock
    ProducerTemplate producerTemplate;


    @Before
    public void setUp(){
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(mockTransferService, "nonHoldingIdInstitutionForTransferApi", nonHoldingIdInstitutionForTransferApi);
        ReflectionTestUtils.setField(helperUtil,"producerTemplate",producerTemplate);
    }

    @Test
    public void processItemTransfer() throws Exception{
        Mockito.when(dummyDataService.getHoldingsWithDummyDetails(Mockito.anyInt(), Mockito.any(), Mockito.anyString(),Mockito.anyString())).thenReturn(getHoldingsEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1, "1421")).thenReturn(getBibliographicEntity());
        Mockito.when(mockTransferService.getHoldingsDetailsRepository().findByOwningInstitutionHoldingsIdAndOwningInstitutionId("1621",1)).thenReturn(getBibliographicEntity().getHoldingsEntities().get(0));
        Mockito.when(mockTransferService.getInstitutionDetailsRepository().findByInstitutionCode(Mockito.any())).thenReturn(getInstitutionEntity());
        Mockito.when(accessionDAO.saveBibRecord(Mockito.any())).thenReturn(getBibliographicEntity());
        Mockito.when(accessionDAO.saveBibRecord(Mockito.any())).thenReturn(getbibliographicEntity());
        Mockito.when(mockTransferService.getInstitutionDetailsRepository().findById(Mockito.any())).thenReturn(Optional.of(getInstitutionEntity()));
        Mockito.doThrow(IOException.class).when(solrIndexService).deleteByDocId(Mockito.anyString(),Mockito.anyString());
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequest(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SUCCESSFULLY_RELINKED,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferSourceDeaccessioned() throws Exception{
        BibliographicEntity bibliographicEntity=getBibliographicEntity();
        bibliographicEntity.getItemEntities().get(0).setDeleted(true);
        Mockito.when(dummyDataService.getHoldingsWithDummyDetails(Mockito.anyInt(), Mockito.any(), Mockito.anyString(),Mockito.anyString())).thenReturn(getHoldingsEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1, "1421")).thenReturn(bibliographicEntity);
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequest(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_ITEM_DEACCESSIONED,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferSourceHoldingNotUnderBib() throws Exception{
        BibliographicEntity bibliographicEntity=getBibliographicEntity();
        bibliographicEntity.setItemEntities(null);
        bibliographicEntity.setHoldingsEntities(null);
        Mockito.when(dummyDataService.getHoldingsWithDummyDetails(Mockito.anyInt(), Mockito.any(), Mockito.anyString(),Mockito.anyString())).thenReturn(getHoldingsEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1, "1421")).thenReturn(bibliographicEntity);
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequest(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_HOLDING_NOT_UNDER_SOURCE_BIB,response.get(0).getMessage());
    }


    @Test
    public void processItemTransferSourceItemNotUnderHolding() throws Exception{
        BibliographicEntity bibliographicEntity=getBibliographicEntity();
        bibliographicEntity.getHoldingsEntities().get(0).setItemEntities(null);
        Mockito.when(dummyDataService.getHoldingsWithDummyDetails(Mockito.anyInt(), Mockito.any(), Mockito.anyString(),Mockito.anyString())).thenReturn(getHoldingsEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1, "1421")).thenReturn(bibliographicEntity);
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequest(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_ITEM_NOT_UNDER_SOURCE_HOLDING,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferSourceHoldingsIdEmpty() throws Exception{
        Mockito.when(dummyDataService.getHoldingsWithDummyDetails(Mockito.anyInt(), Mockito.any(), Mockito.anyString(),Mockito.anyString())).thenReturn(getHoldingsEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1, "1421")).thenReturn(getBibliographicEntity());
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequestSourceHoldingsIdEmpty(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_OWN_INST_HOLDINGS_ID_EMPTY,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferSourceBibIdEmpty() throws Exception{
        Mockito.when(dummyDataService.getHoldingsWithDummyDetails(Mockito.anyInt(), Mockito.any(), Mockito.anyString(),Mockito.anyString())).thenReturn(getHoldingsEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1, "1421")).thenReturn(getBibliographicEntity());
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequestSourceBibIdEmpty(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_OWN_INST_BIB_ID_EMPTY,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferDestinationBibIdEmpty() throws Exception{
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1, "1421")).thenReturn(getBibliographicEntity());
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequest(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.DEST_OWN_INST_BIB_ID_EMPTY,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferBibIdNotExist() throws Exception{
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequest(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_BIB_NOT_EXIST,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferIdsNotMatching() throws Exception{
        Mockito.when(dummyDataService.getHoldingsWithDummyDetails(Mockito.anyInt(), Mockito.any(), Mockito.anyString(),Mockito.anyString())).thenReturn(getHoldingsEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1, "1421")).thenReturn(getBibliographicEntity());
        Mockito.when(mockTransferService.getHoldingsDetailsRepository().findByOwningInstitutionHoldingsIdAndOwningInstitutionId("1621",1)).thenReturn(getBibliographicEntity().getHoldingsEntities().get(0));
        Mockito.when(mockTransferService.getInstitutionDetailsRepository().findByInstitutionCode(Mockito.any())).thenReturn(getInstitutionEntity());
        Mockito.when(accessionDAO.saveBibRecord(Mockito.any())).thenReturn(getBibliographicEntity());
        Mockito.when(accessionDAO.saveBibRecord(Mockito.any())).thenReturn(getbibliographicEntity());
        Mockito.when(mockTransferService.getInstitutionDetailsRepository().findById(Mockito.any())).thenReturn(Optional.of(getInstitutionEntity()));
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequestIdsNotMatching(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_DESTINATION_ITEM_IDS_NOT_MATCHING,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferSourceEmpty() throws Exception{
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequestSourceEmpty(),getInstitutionEntity());
        assertEquals(ScsbConstants.Transfer.SOURCE_EMPTY,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferDestinationEmpty() throws Exception{
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequestDestinationEmpty(),getInstitutionEntity());
        assertEquals(ScsbConstants.Transfer.DESTINATION_EMPTY,response.get(0).getMessage());
    }

    @Test
    public void processItemTransferException() throws Exception{
        BibliographicEntity bibliographicEntity = getbibliographicEntity();
        Mockito.when(dummyDataService.getHoldingsWithDummyDetails(Mockito.anyInt(), Mockito.any(), Mockito.anyString(),Mockito.anyString())).thenThrow(NullPointerException.class);
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1, "1421")).thenReturn(getBibliographicEntity());
        Mockito.when(mockTransferService.getHoldingsDetailsRepository().findByOwningInstitutionHoldingsIdAndOwningInstitutionId("1621",1)).thenReturn(getBibliographicEntity().getHoldingsEntities().get(0));
        Mockito.when(mockTransferService.getInstitutionDetailsRepository().findByInstitutionCode(Mockito.any())).thenReturn(getInstitutionEntity());
        Mockito.when(accessionDAO.saveBibRecord(Mockito.any())).thenReturn(getBibliographicEntity());
        Mockito.when(accessionDAO.saveBibRecord(Mockito.any())).thenReturn(bibliographicEntity);
        Mockito.when(mockTransferService.getInstitutionDetailsRepository().findById(Mockito.any())).thenReturn(Optional.of(getInstitutionEntity()));
        List<ItemTransferResponse> response= mockTransferService.processItemTransfer(getItemTransferRequest(), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.RELINKED_FAILED,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransfer() throws Exception{
        InstitutionEntity institutionEntity = getInstitutionEntity();
        TransferRequest transferRequest = getHoldingTransferRequest(getSource("1421","1621"),getDestination("53642","1621"));
        BibliographicEntity bibliographicEntity = getbibliographicEntity();
        Mockito.doNothing().when(helperUtil).saveReportEntity(Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.anyList());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1,"1421")).thenReturn(getBibliographicEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(Mockito.anyInt(),Mockito.anyString())).thenReturn(bibliographicEntity);
        Mockito.when(mockTransferService.getHoldingsDetailsRepository().findByOwningInstitutionHoldingsIdAndOwningInstitutionId("1621",1)).thenReturn(getBibliographicEntity().getHoldingsEntities().get(0));
        Mockito.when(mockTransferService.getInstitutionDetailsRepository().findByInstitutionCode(Mockito.any())).thenReturn(institutionEntity);
        Mockito.when(accessionDAO.saveBibRecord(getBibliographicEntity())).thenReturn(getBibliographicEntity());
        Mockito.when(accessionDAO.saveBibRecord(Mockito.any())).thenReturn(bibliographicEntity);
        Mockito.when(mockTransferService.processHoldingTransfer(transferRequest, institutionEntity)).thenCallRealMethod();
        Mockito.when(solrIndexService.indexByBibliographicId(Mockito.anyInt())).thenThrow(NullPointerException.class);
        Mockito.doThrow(SolrServerException.class).when(solrIndexService).deleteByDocId(Mockito.anyString(),Mockito.anyString());
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(transferRequest, institutionEntity);
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SUCCESSFULLY_RELINKED,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransferDestinationBibEmpty() throws Exception{
        InstitutionEntity institutionEntity = getInstitutionEntity();
        TransferRequest transferRequest = getHoldingTransferRequest(getSource("1421","1621"),getDestination("","1621"));
        BibliographicEntity bibliographicEntity = getbibliographicEntity();
        Mockito.doNothing().when(helperUtil).saveReportEntity(Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.anyList());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1,"1421")).thenReturn(getBibliographicEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(Mockito.anyInt(),Mockito.anyString())).thenReturn(bibliographicEntity);
        Mockito.when(mockTransferService.getHoldingsDetailsRepository().findByOwningInstitutionHoldingsIdAndOwningInstitutionId("1621",1)).thenReturn(getBibliographicEntity().getHoldingsEntities().get(0));
        Mockito.when(mockTransferService.getInstitutionDetailsRepository().findByInstitutionCode(Mockito.any())).thenReturn(institutionEntity);
        Mockito.when(accessionDAO.saveBibRecord(getBibliographicEntity())).thenReturn(getBibliographicEntity());
        Mockito.when(accessionDAO.saveBibRecord(Mockito.any())).thenReturn(bibliographicEntity);
        Mockito.when(mockTransferService.processHoldingTransfer(transferRequest, institutionEntity)).thenCallRealMethod();
        Mockito.when(solrIndexService.indexByBibliographicId(Mockito.anyInt())).thenThrow(NullPointerException.class);
        Mockito.doThrow(SolrServerException.class).when(solrIndexService).deleteByDocId(Mockito.anyString(),Mockito.anyString());
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(transferRequest, institutionEntity);
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.DEST_OWN_INST_BIB_ID_EMPTY,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransferSourceDeaccessioned() throws Exception{
        InstitutionEntity institutionEntity = getInstitutionEntity();
        TransferRequest transferRequest = getHoldingTransferRequest(getSource("1421","1621"),getDestination("53642","1621"));
        BibliographicEntity bibliographicEntity = getbibliographicEntity();
        BibliographicEntity bibliographicEntity1 = getBibliographicEntity();
        bibliographicEntity.getHoldingsEntities().get(0).setDeleted(true);
        Mockito.doNothing().when(helperUtil).saveReportEntity(Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.anyList());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1,"1421")).thenReturn(bibliographicEntity1);
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(Mockito.anyInt(),Mockito.anyString())).thenReturn(bibliographicEntity);
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(transferRequest, institutionEntity);
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_HOLDING_DEACCESSIONED,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransferDestinationDeaccessioned() throws Exception{
        InstitutionEntity institutionEntity = getInstitutionEntity();
        TransferRequest transferRequest = getHoldingTransferRequest(getSource("1421","1621"),getDestination("53642","1621"));
        BibliographicEntity bibliographicEntity = getbibliographicEntity();
        BibliographicEntity bibliographicEntity1 = getBibliographicEntity();
        bibliographicEntity.setDeleted(true);
        Mockito.doNothing().when(helperUtil).saveReportEntity(Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.anyList());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1,"1421")).thenReturn(bibliographicEntity1);
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(Mockito.anyInt(),Mockito.anyString())).thenReturn(bibliographicEntity);
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(transferRequest, institutionEntity);
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.DEST_BIB_DEACCESSIONED,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransferSourceNotUnderBib() throws Exception{
        InstitutionEntity institutionEntity = getInstitutionEntity();
        TransferRequest transferRequest = getHoldingTransferRequest(getSource("1421","1621"),getDestination("53642","1621"));
        BibliographicEntity bibliographicEntity = getbibliographicEntity();
        BibliographicEntity bibliographicEntity1 = getBibliographicEntity();
        bibliographicEntity.setHoldingsEntities(null);
        Mockito.doNothing().when(helperUtil).saveReportEntity(Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.anyList());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1,"1421")).thenReturn(bibliographicEntity1);
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(Mockito.anyInt(),Mockito.anyString())).thenReturn(bibliographicEntity);
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(transferRequest, institutionEntity);
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_HOLDING_NOT_UNDER_SOURCE_BIB,response.get(0).getMessage());
    }


    @Test
    public void processHoldingTransferSourceEmpty() throws Exception{
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(getHoldingTransferRequest(null,getDestination("53642","1621")), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_EMPTY,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransferDestinationEmpty() throws Exception{
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(getHoldingTransferRequest(getSource("1421","1621"),null), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.DESTINATION_EMPTY,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransferIdsNotMatching() throws Exception{
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(getHoldingTransferRequest(getSource("1421","1622"),getDestination("53642","1621")), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_DESTINATION_HOLDINGS_IDS_NOT_MATCHING,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransferSourceBibEmpty() throws Exception{
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(getHoldingTransferRequest(getSource("","1621"),getDestination("53642","1621")), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_OWN_INST_BIB_ID_EMPTY,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransferSourceBibNonExist() throws Exception{
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(getHoldingTransferRequest(getSource("1421","1621"),getDestination("53642","1621")), getInstitutionEntity());
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.SOURCE_BIB_NOT_EXIST,response.get(0).getMessage());
    }

    @Test
    public void processHoldingTransferException() throws Exception{
        InstitutionEntity institutionEntity = getInstitutionEntity();
        TransferRequest transferRequest = getHoldingTransferRequest(getSource("1421","1621"),getDestination("53642","1621"));
        BibliographicEntity bibliographicEntity = getbibliographicEntity();
        Mockito.doNothing().when(helperUtil).saveReportEntity(Mockito.anyString(),Mockito.anyString(),Mockito.anyString(),Mockito.anyList());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(1,"1421")).thenReturn(getBibliographicEntity());
        Mockito.when(mockTransferService.getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(Mockito.anyInt(),Mockito.anyString())).thenReturn(bibliographicEntity);
        Mockito.when(mockTransferService.getHoldingsDetailsRepository().findByOwningInstitutionHoldingsIdAndOwningInstitutionId("1621",1)).thenReturn(getBibliographicEntity().getHoldingsEntities().get(0));
        Mockito.when(mockTransferService.getInstitutionDetailsRepository().findByInstitutionCode(Mockito.any())).thenReturn(institutionEntity);
        Mockito.when(accessionDAO.saveBibRecord(getBibliographicEntity())).thenReturn(getBibliographicEntity());
        Mockito.when(accessionDAO.saveBibRecord(Mockito.any())).thenThrow(NullPointerException.class);
        Mockito.when(mockTransferService.processHoldingTransfer(transferRequest, institutionEntity)).thenCallRealMethod();
        Mockito.when(solrIndexService.indexByBibliographicId(Mockito.anyInt())).thenThrow(NullPointerException.class);
        List<HoldingTransferResponse> response= mockTransferService.processHoldingTransfer(transferRequest, institutionEntity);
        assertNotNull(response);
        assertEquals(ScsbConstants.Transfer.RELINKED_FAILED,response.get(0).getMessage());
    }


    public HoldingsEntity getHoldingsEntity() throws Exception {
        HoldingsEntity holdingsEntity = new HoldingsEntity();
        holdingsEntity.setContent((ScsbConstants.DUMMY_HOLDING_CONTENT_XML).getBytes());
        holdingsEntity.setCreatedDate(new Date());
        holdingsEntity.setCreatedBy("");
        holdingsEntity.setLastUpdatedDate(new Date());
        holdingsEntity.setOwningInstitutionId(1);
        holdingsEntity.setOwningInstitutionHoldingsId("59753");
        holdingsEntity.setLastUpdatedBy("");
        return holdingsEntity;
    }

    public InstitutionEntity getInstitutionEntity() throws Exception {
        InstitutionEntity institutionEntity = new InstitutionEntity();
        institutionEntity.setId(1);
        institutionEntity.setInstitutionName("Princeton");
        institutionEntity.setInstitutionCode("PUL");
        return institutionEntity;
    }

    public BibliographicEntity getbibliographicEntity() throws Exception {
        BibliographicEntity bibliographicEntity = new BibliographicEntity();
        bibliographicEntity.setContent((ScsbConstants.DUMMY_BIB_CONTENT_XML).getBytes());
        bibliographicEntity.setCreatedDate(new Date());
        bibliographicEntity.setCreatedBy("");
        bibliographicEntity.setLastUpdatedBy("");
        bibliographicEntity.setLastUpdatedDate(new Date());
        bibliographicEntity.setOwningInstitutionId(1);
        bibliographicEntity.setOwningInstitutionBibId("53624");
        bibliographicEntity.setCatalogingStatus(ScsbCommonConstants.INCOMPLETE_STATUS);
        bibliographicEntity.setItemEntities(new ArrayList<>());
        bibliographicEntity.setHoldingsEntities(getBibliographicEntity().getHoldingsEntities());
        bibliographicEntity.setId(54);
        return bibliographicEntity;
    }

    public TransferRequest getHoldingTransferRequest(Source source,Destination destination) throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setInstitution("PUL");
        HoldingsTransferRequest holdingsTransferRequestArrayList = new HoldingsTransferRequest();
        holdingsTransferRequestArrayList.setSource(source);
        holdingsTransferRequestArrayList.setDestination(destination);
        List<HoldingsTransferRequest> HoldingsTransferRequest1 = new ArrayList<>();
        HoldingsTransferRequest1.add(holdingsTransferRequestArrayList);
        transferRequest.setHoldingTransfers(HoldingsTransferRequest1);
        return  transferRequest;
    }

    private Destination getDestination(String OwningInstitutionBibId,String OwningInstitutionHoldingsId) {
        Destination destination = new Destination();
        destination.setOwningInstitutionBibId(OwningInstitutionBibId);
        destination.setOwningInstitutionHoldingsId(OwningInstitutionHoldingsId);
        return destination;
    }

    public TransferRequest getItemTransferRequestSourceEmpty() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setInstitution("PUL");
        HoldingsTransferRequest holdingsTransferRequestArrayList = new HoldingsTransferRequest();
        holdingsTransferRequestArrayList.setSource(null);
        Destination destination = new Destination();
        destination.setOwningInstitutionBibId("53642");
        destination.setOwningInstitutionHoldingsId("59753");
        holdingsTransferRequestArrayList.setDestination(destination);
        List<HoldingsTransferRequest> HoldingsTransferRequest1 = new ArrayList<>();
        HoldingsTransferRequest1.add(holdingsTransferRequestArrayList);
        List<ItemTransferRequest> ItemTransferRequest1 = new ArrayList<>();
        ItemTransferRequest1.add(getItemTransferRequests(null,getItemDestination("59753","53642","6320902")));
        transferRequest.setHoldingTransfers(HoldingsTransferRequest1);
        transferRequest.setItemTransfers(ItemTransferRequest1);
        return  transferRequest;
    }

    public TransferRequest getItemTransferRequestDestinationEmpty() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setInstitution("PUL");
        HoldingsTransferRequest holdingsTransferRequestArrayList = new HoldingsTransferRequest();
        holdingsTransferRequestArrayList.setSource(getSource("1421","1621"));
        Destination destination = new Destination();
        destination.setOwningInstitutionBibId("53642");
        destination.setOwningInstitutionHoldingsId("59753");
        holdingsTransferRequestArrayList.setDestination(destination);
        List<HoldingsTransferRequest> HoldingsTransferRequest1 = new ArrayList<>();
        HoldingsTransferRequest1.add(holdingsTransferRequestArrayList);
        List<ItemTransferRequest> ItemTransferRequest1 = new ArrayList<>();
        ItemTransferRequest1.add(getItemTransferRequests(getItemSource("6320902","1421","1621"),null));
        transferRequest.setHoldingTransfers(HoldingsTransferRequest1);
        transferRequest.setItemTransfers(ItemTransferRequest1);
        return  transferRequest;
    }

    public TransferRequest getItemTransferRequestIdsNotMatching() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setInstitution("PUL");
        HoldingsTransferRequest holdingsTransferRequestArrayList = new HoldingsTransferRequest();
        holdingsTransferRequestArrayList.setSource(getSource("1421","1621"));
        Destination destination = new Destination();
        destination.setOwningInstitutionBibId("53642");
        destination.setOwningInstitutionHoldingsId("59753");
        holdingsTransferRequestArrayList.setDestination(destination);
        List<HoldingsTransferRequest> HoldingsTransferRequest1 = new ArrayList<>();
        HoldingsTransferRequest1.add(holdingsTransferRequestArrayList);
        List<ItemTransferRequest> ItemTransferRequest1 = new ArrayList<>();
        ItemTransferRequest1.add(getItemTransferRequests(getItemSource("6320902","1421","1621"),getItemDestination("59753","53642","6320901")));
        transferRequest.setHoldingTransfers(HoldingsTransferRequest1);
        transferRequest.setItemTransfers(ItemTransferRequest1);
        return transferRequest;
    }

    public TransferRequest getItemTransferRequest() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setInstitution("PUL");
        HoldingsTransferRequest holdingsTransferRequestArrayList = new HoldingsTransferRequest();
        holdingsTransferRequestArrayList.setSource(getSource("1421","1621"));
        Destination destination = new Destination();
        destination.setOwningInstitutionBibId("53642");
        destination.setOwningInstitutionHoldingsId("59753");
        holdingsTransferRequestArrayList.setDestination(destination);
        List<HoldingsTransferRequest> HoldingsTransferRequest1 = new ArrayList<>();
        HoldingsTransferRequest1.add(holdingsTransferRequestArrayList);
        List<ItemTransferRequest> ItemTransferRequest1 = new ArrayList<>();
        ItemTransferRequest1.add(getItemTransferRequests(getItemSource("6320902","1421","1621"),getItemDestination("59753","53642","6320902")));
        transferRequest.setHoldingTransfers(HoldingsTransferRequest1);
        transferRequest.setItemTransfers(ItemTransferRequest1);
        return  transferRequest;
    }

    public TransferRequest getItemTransferRequestSourceHoldingsIdEmpty() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setInstitution("PUL");
        HoldingsTransferRequest holdingsTransferRequestArrayList = new HoldingsTransferRequest();
        holdingsTransferRequestArrayList.setSource(getSource("1421","1621"));
        Destination destination = new Destination();
        destination.setOwningInstitutionBibId("53642");
        destination.setOwningInstitutionHoldingsId("59753");
        holdingsTransferRequestArrayList.setDestination(destination);
        List<HoldingsTransferRequest> HoldingsTransferRequest1 = new ArrayList<>();
        HoldingsTransferRequest1.add(holdingsTransferRequestArrayList);
        List<ItemTransferRequest> ItemTransferRequest1 = new ArrayList<>();
        ItemTransferRequest1.add(getItemTransferRequests(getItemSource("6320902","1421",""),getItemDestination("59753","53642","6320902")));
        transferRequest.setHoldingTransfers(HoldingsTransferRequest1);
        transferRequest.setItemTransfers(ItemTransferRequest1);
        return  transferRequest;
    }

    private ItemTransferRequest getItemTransferRequests(ItemSource itemSource,ItemDestination itemDestination) {
        ItemTransferRequest itemTransferRequest = new ItemTransferRequest();
        itemTransferRequest.setSource(itemSource);
        itemTransferRequest.setDestination(itemDestination);
        return itemTransferRequest;
    }

    public TransferRequest getItemTransferRequestSourceBibIdEmpty() throws Exception {
        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setInstitution("PUL");
        HoldingsTransferRequest holdingsTransferRequestArrayList = new HoldingsTransferRequest();
        holdingsTransferRequestArrayList.setSource(getSource("1421","1621"));
        Destination destination = new Destination();
        destination.setOwningInstitutionBibId("53642");
        destination.setOwningInstitutionHoldingsId("59753");
        holdingsTransferRequestArrayList.setDestination(destination);
        List<HoldingsTransferRequest> HoldingsTransferRequest1 = new ArrayList<>();
        HoldingsTransferRequest1.add(holdingsTransferRequestArrayList);
        List<ItemTransferRequest> ItemTransferRequest1 = new ArrayList<>();
        ItemTransferRequest1.add(getItemTransferRequests(getItemSource("6320902","","1621"),getItemDestination("59753","53642","6320902")));
        transferRequest.setHoldingTransfers(HoldingsTransferRequest1);
        transferRequest.setItemTransfers(ItemTransferRequest1);
        return  transferRequest;
    }

    private ItemDestination getItemDestination(String OwningInstitutionHoldingsId,String OwningInstitutionBibId,String OwningInstitutionItemId) {
        ItemDestination itemDestination = new ItemDestination();
        itemDestination.setOwningInstitutionHoldingsId(OwningInstitutionHoldingsId);
        itemDestination.setOwningInstitutionBibId(OwningInstitutionBibId);
        itemDestination.setOwningInstitutionItemId(OwningInstitutionItemId);
        return itemDestination;
    }

    private ItemSource getItemSource(String OwningInstitutionItemId,String OwningInstitutionBibId,String OwningInstitutionHoldingsId) {
        ItemSource itemSource = new ItemSource();
        itemSource.setOwningInstitutionItemId(OwningInstitutionItemId);
        itemSource.setOwningInstitutionBibId(OwningInstitutionBibId);
        itemSource.setOwningInstitutionHoldingsId(OwningInstitutionHoldingsId);
        return itemSource;
    }

    private Source getSource(String OwningInstitutionBibId,String OwningInstitutionHoldingsId) {
        Source source = new Source();
        source.setOwningInstitutionBibId(OwningInstitutionBibId);
        source.setOwningInstitutionHoldingsId(OwningInstitutionHoldingsId);
        return source;
    }

        public BibliographicEntity getBibliographicEntity() throws Exception {
        Date today = new Date();
        BibliographicEntity bibliographicEntity = new BibliographicEntity();
        bibliographicEntity.setContent("mock Content".getBytes());
        bibliographicEntity.setCreatedDate(new Date());
        bibliographicEntity.setLastUpdatedDate(new Date());
        bibliographicEntity.setCreatedBy("tst");
        bibliographicEntity.setLastUpdatedBy("tst");
        bibliographicEntity.setOwningInstitutionId(1);
        bibliographicEntity.setOwningInstitutionBibId("1421");
        bibliographicEntity.setId(1);
        List<BibliographicEntity> bibliographicEntitylist = new LinkedList(Arrays.asList(bibliographicEntity));


        HoldingsEntity holdingsEntity = new HoldingsEntity();
        holdingsEntity.setContent("mock holdings".getBytes());
        holdingsEntity.setCreatedDate(today);
        holdingsEntity.setLastUpdatedDate(today);
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
}
