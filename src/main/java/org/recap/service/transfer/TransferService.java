package org.recap.service.transfer;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.jpa.*;
import org.recap.model.transfer.*;
import org.recap.repository.jpa.BibliographicDetailsRepository;
import org.recap.repository.jpa.HoldingsDetailsRepository;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.service.accession.AccessionDAO;
import org.recap.service.accession.DummyDataService;
import org.recap.service.accession.SolrIndexService;
import org.recap.util.HelperUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;


/**
 * Created by sheiks on 19/07/17.
 */
@Service
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class TransferService {

    private static final Logger logger = LoggerFactory.getLogger(TransferService.class);


    @Autowired
    BibliographicDetailsRepository bibliographicDetailsRepository;

    @Autowired
    AccessionDAO accessionDAO;

    @Autowired
    HoldingsDetailsRepository holdingsDetailsRepository;

    @Autowired
    InstitutionDetailsRepository institutionDetailsRepository;

    @Autowired
    DummyDataService dummyDataService;

    @Autowired
    SolrIndexService solrIndexService;

    @Autowired
    HelperUtil helperUtil;

    @Value("${" + PropertyKeyConstants.TRANSFER_API_NONHOLDINGID_INSTITUTION + "}")
    private String nonHoldingIdInstitutionForTransferApi;

    public BibliographicDetailsRepository getBibliographicDetailsRepository() {
        return bibliographicDetailsRepository;
    }

    public HelperUtil getHelperUtil() {
        return helperUtil;
    }

    public HoldingsDetailsRepository getHoldingsDetailsRepository() {
        return holdingsDetailsRepository;
    }

    @Transactional
    public List<ItemTransferResponse> processItemTransfer(TransferRequest transferRequest, InstitutionEntity institutionEntity) {
        List<ItemTransferResponse> itemTransferResponses = new ArrayList<>();
        List<ItemTransferRequest> itemTransfers = transferRequest.getItemTransfers();
        if(CollectionUtils.isNotEmpty(itemTransfers)) {
            for (Iterator<ItemTransferRequest> iterator = itemTransfers.iterator(); iterator.hasNext(); ) {
                ItemTransferRequest itemTransferRequest = iterator.next();
                TransferValidationResponse transferValidationResponse = validateItemTransferRequest(itemTransferRequest, institutionEntity.getId());
                if(transferValidationResponse.isValid()) {
                    try {
                        Date currentDate = new Date();
                        BibliographicEntity sourceBib = transferValidationResponse.getSourceBib();
                        HoldingsEntity sourceHoldings = transferValidationResponse.getSourceHoldings();
                        ItemEntity sourceItem = transferValidationResponse.getSourceItem();

                        BibliographicEntity destBib = getDestinationBib(transferValidationResponse, currentDate,institutionEntity.getId());
                        HoldingsEntity destHoldings = getDestinationHoldings(transferValidationResponse, currentDate, institutionEntity.getId());


                        // todo : unlink from source
                        unlinkRecordsForItemTransfer(sourceBib, sourceHoldings, sourceItem, currentDate);

                        // todo : link to destination
                        linkRecordsForItemTransfer(destBib, destHoldings, sourceItem, currentDate);

                        // todo : process for orphan records
                        processOrphanRecords(sourceBib);
                        processOrphanRecords(destBib);

                        Map<String, Set<Integer>> recordToDelete = new HashMap<>();
                        Map<String, Set<Integer>> recordToIndex = new HashMap<>();
                        addRecordToMap(ScsbCommonConstants.ITEM_ID, sourceItem.getId(), recordToDelete);

                        List<BibliographicEntity> itemBibEntities = sourceItem.getBibliographicEntities();
                        if(CollectionUtils.isNotEmpty(itemBibEntities)) {
                            for (Iterator<BibliographicEntity> bibliographicEntityIterator = itemBibEntities.iterator(); bibliographicEntityIterator.hasNext(); ) {
                                BibliographicEntity bibliographicEntity = bibliographicEntityIterator.next();
                                addRecordToMap(ScsbCommonConstants.BIB_ID, bibliographicEntity.getId(), recordToIndex);
                            }
                        }

                        saveAndIndexBib(sourceBib, sourceHoldings, destBib, destHoldings, Arrays.asList(sourceItem), recordToDelete, recordToIndex);

                        transferValidationResponse.setMessage(ScsbConstants.Transfer.SUCCESSFULLY_RELINKED);
                    } catch (Exception e) {
                        logger.error(ScsbCommonConstants.LOG_ERROR,e);
                        transferValidationResponse.setMessage(ScsbConstants.Transfer.RELINKED_FAILED);
                    }
                }
                ItemTransferResponse itemTransferResponse = new ItemTransferResponse(transferValidationResponse.getMessage(), itemTransferRequest, transferValidationResponse.isValid());
                itemTransferResponses.add(itemTransferResponse);

                String requestString = getHelperUtil().getJsonString(itemTransferRequest);
                String responseString = transferValidationResponse.getMessage();
                saveReportForTransfer(requestString, responseString, institutionEntity.getInstitutionCode(), ScsbConstants.Transfer.TransferTypes.ITEM_TRANSFER);
            }
        }
        return itemTransferResponses;
    }

    private TransferValidationResponse validateItemTransferRequest(ItemTransferRequest itemTransferRequest, Integer institutionId) {
        TransferValidationResponse transferValidationResponse = new TransferValidationResponse(true);
        ItemSource source = itemTransferRequest.getSource();
        if(null != source) {
            ItemDestination destination = itemTransferRequest.getDestination();
            if(null != destination) {
                if(StringUtils.equals(source.getOwningInstitutionItemId(), destination.getOwningInstitutionItemId())) {
                    validSourceBibAndHoldingsAndItem(source, institutionId, transferValidationResponse);
                    if(transferValidationResponse.isValid()) {
                        validDestinationBibAndHoldingsAndItem(destination, institutionId, transferValidationResponse);
                    }
                } else {
                    transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_DESTINATION_ITEM_IDS_NOT_MATCHING);
                }

            } else {
                transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DESTINATION_EMPTY);
            }

        } else {
            transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_EMPTY);
        }
        return transferValidationResponse;
    }

    private void validDestinationBibAndHoldingsAndItem(ItemDestination destination, Integer owningInstitutionId, TransferValidationResponse transferValidationResponse) {
        String owningInstitutionBibId = destination.getOwningInstitutionBibId();
        String owningInstitutionHoldingsId;
        Optional<InstitutionEntity> fetchedInstitutionEntity = institutionDetailsRepository.findById(owningInstitutionId);
        if(fetchedInstitutionEntity.isPresent()) {
        String[] nonHoldingIdInstitutionArray = nonHoldingIdInstitutionForTransferApi.split(",");
            boolean isNonHoldingIdInstitution = Arrays.asList(nonHoldingIdInstitutionArray).contains(fetchedInstitutionEntity.get().getInstitutionCode());

            if (StringUtils.isNotBlank(owningInstitutionBibId)) {
                BibliographicEntity fetchedBibliographicEntity = bibliographicDetailsRepository.findByOwningInstitutionIdAndOwningInstitutionBibId(owningInstitutionId, owningInstitutionBibId);
                if (fetchedBibliographicEntity == null && StringUtils.isBlank(destination.getOwningInstitutionHoldingsId()) && isNonHoldingIdInstitution) {
                    owningInstitutionHoldingsId = UUID.randomUUID().toString();
                } else {
                    owningInstitutionHoldingsId = destination.getOwningInstitutionHoldingsId();
                }

                if (StringUtils.isNotBlank(owningInstitutionHoldingsId)) {
                    String owningInstitutionItemId = destination.getOwningInstitutionItemId();
                    if (StringUtils.isNotBlank(owningInstitutionItemId)) {
                        BibliographicEntity destinationBibEntity =
                                getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(owningInstitutionId, owningInstitutionBibId);
                        if (null != destinationBibEntity) {
                            transferValidationResponse.setDestBib(destinationBibEntity);
                            validateHoldingsEntity(owningInstitutionId, transferValidationResponse, owningInstitutionHoldingsId, destinationBibEntity, true);
                        } else {
                            transferValidationResponse.setDestinationBibId(owningInstitutionBibId);
                            validateHoldingsEntity(owningInstitutionId, transferValidationResponse, owningInstitutionHoldingsId, destinationBibEntity, false);
                        }
                    } else {
                        transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DEST_OWN_INST_ITEM_ID_EMPTY);
                    }
                } else {
                    transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DEST_OWN_INST_HOLDINGS_ID_EMPTY);
                }

            }
        }else {
            transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DEST_OWN_INST_BIB_ID_EMPTY);
        }
    }

    private void validateHoldingsEntity(Integer owningInstitutionId, TransferValidationResponse transferValidationResponse, String owningInstitutionHoldingsId, BibliographicEntity destinationBibEntity, boolean retrieveHoldingFromBib) {
        HoldingsEntity holdingsEntity = null;
        if (retrieveHoldingFromBib) {
            holdingsEntity = matchHoldingIdWithHoldings(owningInstitutionHoldingsId, destinationBibEntity);
        }
        if(null == holdingsEntity) {
            // todo : check with other entity
            holdingsEntity = getHoldingsDetailsRepository().
                    findByOwningInstitutionHoldingsIdAndOwningInstitutionId(owningInstitutionHoldingsId, owningInstitutionId);
            if(null != holdingsEntity) {
                transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DEST_HOLDINGS_ATTACHED_WITH_DIFF_BIB);
            }
        }
        else if (holdingsEntity.isDeleted()){
            transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DEST_HOLDING_DEACCESSIONED);
        }
        else {
            transferValidationResponse.setDestHoldings(holdingsEntity);
        }
        transferValidationResponse.setDestHoldingsId(owningInstitutionHoldingsId);
    }

    private void validSourceBibAndHoldingsAndItem(ItemSource source, Integer owningInstitutionId, TransferValidationResponse transferValidationResponse) {
        String owningInstitutionBibId = source.getOwningInstitutionBibId();
        if(StringUtils.isNotBlank(owningInstitutionBibId)) {
            String owningInstitutionHoldingsId = source.getOwningInstitutionHoldingsId();
            if(StringUtils.isNotBlank(owningInstitutionHoldingsId)) {
                String owningInstitutionItemId = source.getOwningInstitutionItemId();
                if(StringUtils.isNotBlank(owningInstitutionItemId)) {
                    BibliographicEntity sourceBibEntity = getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(owningInstitutionId, owningInstitutionBibId);
                    if(null != sourceBibEntity) {
                        transferValidationResponse.setSourceBib(sourceBibEntity);
                        HoldingsEntity holdingsEntity = matchHoldingIdWithHoldings(owningInstitutionHoldingsId, sourceBibEntity);
                        if (holdingsEntity == null) {
                            transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_HOLDING_NOT_UNDER_SOURCE_BIB);
                        } else {
                            transferValidationResponse.setSourceHoldings(holdingsEntity);
                            ItemEntity itemEntity = matchItemIdWithItem(owningInstitutionItemId, holdingsEntity,transferValidationResponse);
                            if (null == itemEntity) {
                                if (StringUtils.isBlank(transferValidationResponse.getMessage())) {
                                    transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_ITEM_NOT_UNDER_SOURCE_HOLDING);
                                }
                            } else {
                                transferValidationResponse.setSourceItem(itemEntity);
                            }
                        }
                    } else {
                        transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_BIB_NOT_EXIST);
                    }
                } else {
                    transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_OWN_INST_ITEM_ID_EMPTY);
                }
            } else {
                transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_OWN_INST_HOLDINGS_ID_EMPTY);
            }
        } else {
            transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_OWN_INST_BIB_ID_EMPTY);
        }
    }

    private ItemEntity matchItemIdWithItem(String owningInstitutionItemId, HoldingsEntity holdingsEntity,TransferValidationResponse transferValidationResponse) {
        List<ItemEntity> itemEntities = holdingsEntity.getItemEntities();
        if(CollectionUtils.isNotEmpty(itemEntities)) {
            for (Iterator<ItemEntity> iterator = itemEntities.iterator(); iterator.hasNext(); ) {
                ItemEntity itemEntity = iterator.next();
                if(StringUtils.equals(itemEntity.getOwningInstitutionItemId(), owningInstitutionItemId)) {
                    if(!itemEntity.isDeleted()) {
                        return itemEntity;
                    }
                    else {
                        transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_ITEM_DEACCESSIONED);
                        return null;
                    }
                }
            }
        }
        return null;
    }

    @Transactional
    public List<HoldingTransferResponse> processHoldingTransfer(TransferRequest transferRequest, InstitutionEntity institutionEntity) {
        List<HoldingTransferResponse> holdingTransferResponses = new ArrayList<>();
        List<HoldingsTransferRequest> holdingTransfers = transferRequest.getHoldingTransfers();
        if(CollectionUtils.isNotEmpty(holdingTransfers)) {
            for (Iterator<HoldingsTransferRequest> iterator = holdingTransfers.iterator(); iterator.hasNext(); ) {
                HoldingsTransferRequest holdingsTransferRequest = iterator.next();
                Integer institutionId = institutionEntity.getId();
                TransferValidationResponse transferValidationResponse =
                        validateHoldingTransferRequest(holdingsTransferRequest, institutionId);
                if(transferValidationResponse.isValid()) {
                    try {
                        Date currentDate = new Date();
                        BibliographicEntity sourceBib = transferValidationResponse.getSourceBib();
                        HoldingsEntity sourceHoldings = transferValidationResponse.getSourceHoldings();
                        ItemEntity sourceItem = transferValidationResponse.getSourceItem();

                        BibliographicEntity destBib = getDestinationBib(transferValidationResponse, currentDate, institutionId);

                        // todo : unlink from source
                        unlinkRecords(sourceBib, sourceHoldings);

                        // todo : link to destination
                        linkRecords(destBib, sourceHoldings, currentDate);

                        sourceBib.setLastUpdatedDate(currentDate);
                        sourceBib.setLastUpdatedBy(ScsbConstants.Transfer.TRANSFER_REQUEST);

                        // todo : process for orphan records
                        processOrphanRecords(sourceBib);
                        processOrphanRecords(destBib);

                        Map<String, Set<Integer>> recordToDelete = new HashMap<>();
                        Map<String, Set<Integer>> recordToIndex = new HashMap<>();

                        addRecordToMap(ScsbCommonConstants.HOLDING_ID, sourceHoldings.getId(), recordToDelete);

                        List<BibliographicEntity> bibliographicEntities = sourceHoldings.getBibliographicEntities();
                        if(CollectionUtils.isNotEmpty(bibliographicEntities)) {
                            for (Iterator<BibliographicEntity> bibliographicEntityIterator = bibliographicEntities.iterator(); bibliographicEntityIterator.hasNext(); ) {
                                BibliographicEntity bibliographicEntity = bibliographicEntityIterator.next();
                                addRecordToMap(ScsbCommonConstants.BIB_ID, bibliographicEntity.getId(), recordToIndex);
                            }
                        }

                        List<ItemEntity> itemEntities = sourceHoldings.getItemEntities();
                        if (CollectionUtils.isNotEmpty(itemEntities)) {
                            for (Iterator<ItemEntity> itemEntityIterator = itemEntities.iterator(); itemEntityIterator.hasNext(); ) {
                                ItemEntity itemEntity = itemEntityIterator.next();
                                addRecordToMap(ScsbCommonConstants.ITEM_ID, itemEntity.getId(), recordToDelete);

                                List<BibliographicEntity> itemBibEntities = itemEntity.getBibliographicEntities();
                                if(CollectionUtils.isNotEmpty(itemBibEntities)) {
                                    for (Iterator<BibliographicEntity> bibliographicEntityIterator = itemBibEntities.iterator(); bibliographicEntityIterator.hasNext(); ) {
                                        BibliographicEntity bibliographicEntity = bibliographicEntityIterator.next();
                                        addRecordToMap(ScsbCommonConstants.BIB_ID, bibliographicEntity.getId(), recordToIndex);
                                    }
                                }
                            }
                        }


                        saveAndIndexBib(sourceBib, sourceHoldings, destBib, sourceHoldings, itemEntities, recordToDelete, recordToIndex);

                        transferValidationResponse.setMessage(ScsbConstants.Transfer.SUCCESSFULLY_RELINKED);
                    } catch (Exception e) {
                        logger.error(ScsbCommonConstants.LOG_ERROR,e);
                        transferValidationResponse.setMessage(ScsbConstants.Transfer.RELINKED_FAILED);
                    }

                }
                HoldingTransferResponse holdingTransferResponse = new HoldingTransferResponse(transferValidationResponse.getMessage(), holdingsTransferRequest, transferValidationResponse.isValid());
                holdingTransferResponses.add(holdingTransferResponse);

                String requestString = getHelperUtil().getJsonString(holdingsTransferRequest);
                String responseString = transferValidationResponse.getMessage();
                saveReportForTransfer(requestString, responseString, institutionEntity.getInstitutionCode(), ScsbConstants.Transfer.TransferTypes.HOLDINGS_TRANSFER);
            }
        }

        return holdingTransferResponses;
    }

    private void addRecordToMap(String key, Integer value, Map<String, Set<Integer>> recordToDelete) {
        if (null != value) {
            Set<Integer> ids = recordToDelete.computeIfAbsent(key, k -> null);
            if(null == ids) {
                ids = new HashSet<>();
            }
            ids.add(value);
            recordToDelete.put(key, ids);
        }
    }

    private void saveAndIndexBib(BibliographicEntity sourceBib, HoldingsEntity sourceHoldings, BibliographicEntity destBib,
                                 HoldingsEntity destHoldings, List<ItemEntity> itemEntities, Map<String, Set<Integer>> recordToDelete, Map<String, Set<Integer>> recordToIndex) {
        BibliographicEntity savevdSourceBibRecord = accessionDAO.saveBibRecord(sourceBib);
        BibliographicEntity saveBibRecord = accessionDAO.saveBibRecord(destBib);

        writeChangeLog(sourceBib.getId(), sourceHoldings.getId(), itemEntities, destBib.getId(), destHoldings.getId());

        deleteRecordForRelink(recordToDelete);

        addRecordToMap(ScsbCommonConstants.BIB_ID, savevdSourceBibRecord.getId(), recordToIndex);
        addRecordToMap(ScsbCommonConstants.BIB_ID, saveBibRecord.getId(), recordToIndex);

        indexRecordForRelink(recordToIndex);

    }

    private void deleteRecordForRelink(Map<String, Set<Integer>> recordToDelete) {
        if(recordToDelete.size() > 0) {
            for (Iterator<String> iterator = recordToDelete.keySet().iterator(); iterator.hasNext(); ) {
                String docId = iterator.next();
                Set<Integer> ids = recordToDelete.get(docId);
                if(CollectionUtils.isNotEmpty(ids)) {
                    for (Iterator<Integer> stringIterator = ids.iterator(); stringIterator.hasNext(); ) {
                        Integer id = stringIterator.next();
                        try {
                            logger.info("deleting {} from solr for relink, {} - {}, ",docId,docId,id);
                            solrIndexService.deleteByDocId(docId, String.valueOf(id));
                        } catch (IOException | SolrServerException e) {
                            logger.error(ScsbCommonConstants.LOG_ERROR,e);
                        }
                    }
                }
            }
        }
    }

    private void indexRecordForRelink(Map<String, Set<Integer>> recordToIndex) {
        if(recordToIndex.size() > 0) {
            for (Iterator<String> iterator = recordToIndex.keySet().iterator(); iterator.hasNext(); ) {
                String docId = iterator.next();
                Set<Integer> ids = recordToIndex.get(docId);
                if(CollectionUtils.isNotEmpty(ids)) {
                    for (Iterator<Integer> stringIterator = ids.iterator(); stringIterator.hasNext(); ) {
                        Integer id = stringIterator.next();
                        try {
                            solrIndexService.indexByBibliographicId(id);
                        } catch (Exception e) {
                            logger.error(ScsbCommonConstants.LOG_ERROR,e);
                        }
                    }
                }
            }
        }
    }

    private void processOrphanRecords(BibliographicEntity sourceBib) {
        List<HoldingsEntity> holdingsEntities = sourceBib.getHoldingsEntities();
        if(CollectionUtils.isEmpty(holdingsEntities)) {
            sourceBib.setDeleted(true);
        } else {
            boolean allDeleted = true;
            for (HoldingsEntity holdingsEntity : holdingsEntities) {
                boolean allItemDeleted = allItemDeleted(holdingsEntity);
                holdingsEntity.setDeleted(allItemDeleted);
                allDeleted = allDeleted && holdingsEntity.isDeleted();
            }
            sourceBib.setDeleted(allDeleted);
        }
    }

    private boolean allItemDeleted(HoldingsEntity holdingsEntity) {
        List<ItemEntity> itemEntities = holdingsEntity.getItemEntities();
        boolean allItemDeleted = true;
        if(CollectionUtils.isNotEmpty(itemEntities)){
            for (ItemEntity itemEntity : itemEntities) {
                allItemDeleted = allItemDeleted && itemEntity.isDeleted();
            }
        }
        return allItemDeleted;
    }

    private void unlinkRecords(BibliographicEntity sourceBib, HoldingsEntity sourceHoldings) {
        sourceHoldings.getBibliographicEntities().remove(sourceBib);
        sourceBib.getHoldingsEntities().remove(sourceHoldings);
        List<ItemEntity> itemEntities = sourceHoldings.getItemEntities();
        if(CollectionUtils.isNotEmpty(itemEntities)) {
            for (ItemEntity itemEntity : itemEntities) {
                itemEntity.getBibliographicEntities().remove(sourceBib);
            }
        }
        sourceBib.getItemEntities().removeAll(itemEntities);
    }

    private void unlinkRecordsForItemTransfer(BibliographicEntity sourceBib, HoldingsEntity sourceHoldings, ItemEntity sourceItem, Date updatedDate) {
        sourceItem.getBibliographicEntities().remove(sourceBib);
        sourceItem.getHoldingsEntities().remove(sourceHoldings);
        sourceBib.getItemEntities().remove(sourceItem);
        sourceHoldings.getItemEntities().remove(sourceItem);

        sourceBib.setLastUpdatedDate(updatedDate);
        sourceBib.setLastUpdatedBy(ScsbConstants.Transfer.TRANSFER_REQUEST);
        sourceHoldings.setLastUpdatedDate(updatedDate);
        sourceHoldings.setLastUpdatedBy(ScsbConstants.Transfer.TRANSFER_REQUEST);
    }

    private void linkRecords(BibliographicEntity destBib, HoldingsEntity sourceHoldings, Date updatedDate) {
        sourceHoldings.getBibliographicEntities().add(destBib);
        sourceHoldings.setLastUpdatedDate(updatedDate);
        sourceHoldings.setLastUpdatedBy(ScsbConstants.Transfer.TRANSFER_REQUEST);

        List<ItemEntity> itemEntities = sourceHoldings.getItemEntities();
        if(CollectionUtils.isNotEmpty(itemEntities)) {
            for (Iterator<ItemEntity> iterator = itemEntities.iterator(); iterator.hasNext(); ) {
                ItemEntity itemEntity = iterator.next();
                itemEntity.getBibliographicEntities().add(destBib);
                itemEntity.setLastUpdatedDate(updatedDate);
                itemEntity.setLastUpdatedBy(ScsbConstants.Transfer.TRANSFER_REQUEST);
            }
        }
        destBib.getItemEntities().addAll(itemEntities);
        destBib.getHoldingsEntities().add(sourceHoldings);

        destBib.setLastUpdatedDate(updatedDate);
        destBib.setLastUpdatedBy(ScsbConstants.Transfer.TRANSFER_REQUEST);
    }

    private void linkRecordsForItemTransfer(BibliographicEntity destBib, HoldingsEntity destHoldings, ItemEntity sourceItem, Date updatedDate) {
        sourceItem.getHoldingsEntities().add(destHoldings);
        sourceItem.getBibliographicEntities().add(destBib);

        destHoldings.getItemEntities().add(sourceItem);
        destBib.getItemEntities().add(sourceItem);

        if(!destHoldings.getBibliographicEntities().contains(destBib)) {
            destHoldings.getBibliographicEntities().add(destBib);
        }

        if(!destBib.getHoldingsEntities().contains(destHoldings)) {
            destBib.getHoldingsEntities().add(destHoldings);
        }

        destBib.setLastUpdatedDate(updatedDate);
        destHoldings.setLastUpdatedBy(ScsbConstants.Transfer.TRANSFER_REQUEST);
        sourceItem.setLastUpdatedDate(updatedDate);
        sourceItem.setLastUpdatedBy(ScsbConstants.Transfer.TRANSFER_REQUEST);
        destBib.setLastUpdatedDate(updatedDate);
        destBib.setLastUpdatedBy(ScsbConstants.Transfer.TRANSFER_REQUEST);
    }

    private TransferValidationResponse validateHoldingTransferRequest(HoldingsTransferRequest holdingsTransferRequest, Integer institutionId) {
        TransferValidationResponse transferValidationResponse = new TransferValidationResponse(true);
        Source source = holdingsTransferRequest.getSource();
        if(null != source) {
            Destination destination = holdingsTransferRequest.getDestination();
            if(null != destination) {

                if(StringUtils.equals(source.getOwningInstitutionHoldingsId(), destination.getOwningInstitutionHoldingsId())) {
                    validSourceBibAndHoldings(source, institutionId, transferValidationResponse);
                    if(transferValidationResponse.isValid()) {
                        validDestinationBibAndHoldings(destination, institutionId, transferValidationResponse);
                    } else {
                        return transferValidationResponse;
                    }
                } else {
                    transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_DESTINATION_HOLDINGS_IDS_NOT_MATCHING);
                }

            } else {
                transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DESTINATION_EMPTY);
            }

        } else {
            transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_EMPTY);
        }
        return transferValidationResponse;
    }

    private HoldingsEntity getDestinationHoldings(TransferValidationResponse holdingTransferValidationResponse, Date currentDate,Integer institutionId) {
        HoldingsEntity destHoldings = holdingTransferValidationResponse.getDestHoldings();
        if(null == destHoldings) {
            destHoldings = dummyDataService.getHoldingsWithDummyDetails(institutionId, currentDate, ScsbConstants.Transfer.TRANSFER_REQUEST, holdingTransferValidationResponse.getDestHoldingsId());
            destHoldings.setBibliographicEntities(new ArrayList<>());
            destHoldings.setItemEntities(new ArrayList<>());
        }
        return destHoldings;
    }

    private BibliographicEntity getDestinationBib(TransferValidationResponse transferValidationResponse, Date currentDate, Integer institutionId) {
        BibliographicEntity destBib = transferValidationResponse.getDestBib();
        if(null == destBib) {
            destBib = new BibliographicEntity();
            dummyDataService.updateBibWithDummyDetails(institutionId, destBib, currentDate, ScsbConstants.Transfer.TRANSFER_REQUEST, transferValidationResponse.getDestinationBibId());
            destBib.setItemEntities(new ArrayList<>());
            destBib.setHoldingsEntities(new ArrayList<>());
        }
        return destBib;
    }

    private TransferValidationResponse validDestinationBibAndHoldings(Destination destination, Integer owningInstitutionId,
                                                                      TransferValidationResponse transferValidationResponse) {
        String owningInstitutionBibId = destination.getOwningInstitutionBibId();
        if(StringUtils.isNotBlank(owningInstitutionBibId)) {
            String owningInstitutionHoldingsId = destination.getOwningInstitutionHoldingsId();
            if(StringUtils.isNotBlank(owningInstitutionHoldingsId)) {
                BibliographicEntity destinationBibEntity =
                        getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(owningInstitutionId, owningInstitutionBibId);
                if(null != destinationBibEntity) {
                    if(!destinationBibEntity.isDeleted()) {
                        transferValidationResponse.setDestBib(destinationBibEntity);
                    }
                    else {
                        transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DEST_BIB_DEACCESSIONED);
                    }
                } else {
                    transferValidationResponse.setDestinationBibId(owningInstitutionBibId);
                }
            } else {
                transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DEST_OWN_INST_HOLDINGS_ID_EMPTY);
            }

        } else {
            transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.DEST_OWN_INST_BIB_ID_EMPTY);
        }
        return transferValidationResponse;
    }

    private void validSourceBibAndHoldings(Source source, Integer owningInstitutionId, TransferValidationResponse transferValidationResponse) {
        String owningInstitutionBibId = source.getOwningInstitutionBibId();
        if(StringUtils.isNotBlank(owningInstitutionBibId)) {
            String owningInstitutionHoldingsId = source.getOwningInstitutionHoldingsId();
            if(StringUtils.isNotBlank(owningInstitutionHoldingsId)) {
                BibliographicEntity sourceBibEntity = getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(owningInstitutionId, owningInstitutionBibId);
                if(null != sourceBibEntity) {
                    transferValidationResponse.setSourceBib(sourceBibEntity);
                    HoldingsEntity holdingsEntity = matchHoldingIdWithHoldings(owningInstitutionHoldingsId, sourceBibEntity);
                    if (holdingsEntity == null) {
                        transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_HOLDING_NOT_UNDER_SOURCE_BIB);
                    }
                    else if(holdingsEntity.isDeleted()){
                        transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_HOLDING_DEACCESSIONED);
                    }
                    else {
                        transferValidationResponse.setSourceHoldings(holdingsEntity);
                    }
                } else {
                    transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_BIB_NOT_EXIST);
                }
            } else {
                transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_OWN_INST_HOLDINGS_ID_EMPTY);
            }
        } else {
            transferValidationResponse.setInvalidWithMessage(ScsbConstants.Transfer.SOURCE_OWN_INST_BIB_ID_EMPTY);
        }
    }

    private HoldingsEntity matchHoldingIdWithHoldings(String owningInstitutionHoldingsId, BibliographicEntity bibliographicEntity) {
        List<HoldingsEntity> holdingsEntities = bibliographicEntity.getHoldingsEntities();
        if(CollectionUtils.isNotEmpty(holdingsEntities)) {
            for (Iterator<HoldingsEntity> iterator = holdingsEntities.iterator(); iterator.hasNext(); ) {
                HoldingsEntity holdingsEntity = iterator.next();
                if(StringUtils.equals(holdingsEntity.getOwningInstitutionHoldingsId(), owningInstitutionHoldingsId)) {
                    return holdingsEntity;
                }
            }
        }
        return null;
    }

    /**
     * Create report data entity list for transfer API.
     *
     *
     * @param institution
     * @param requestString
     * @param responseString
     * @param transferType
     * @return the list of ReportDataEntity
     */
    public List<ReportDataEntity> createReportDataEntityList(String institution, String requestString, String responseString, String transferType){
        List<ReportDataEntity> reportDataEntityList = new ArrayList<>();
        addToReportDataEntityList(transferType, reportDataEntityList, ScsbConstants.Transfer.TRANSFER_TYPE);
        addToReportDataEntityList(institution, reportDataEntityList, ScsbConstants.Transfer.INSTITUTION);
        addToReportDataEntityList(requestString, reportDataEntityList, ScsbConstants.Transfer.REQUEST);
        addToReportDataEntityList(responseString, reportDataEntityList, ScsbConstants.Transfer.RESPONSE);
        return reportDataEntityList;
    }

    private void addToReportDataEntityList(String transferType, List<ReportDataEntity> reportDataEntityList, String transferType2) {
        ReportDataEntity reportDataEntityTransferType = new ReportDataEntity();
        reportDataEntityTransferType.setHeaderName(transferType2);
        reportDataEntityTransferType.setHeaderValue(transferType);
        reportDataEntityList.add(reportDataEntityTransferType);
    }

    public void saveReportForTransfer(String requestString, String responseString, String institution, String transferType) {
        List<ReportDataEntity> reportDataEntityList = new ArrayList<>(createReportDataEntityList(institution,
                requestString, responseString, transferType));
        helperUtil.saveReportEntity(institution, ScsbConstants.TRANSFER_REPORT, ScsbConstants.TRANSFER_REPORT, reportDataEntityList);
    }

    public void writeChangeLog(Integer sourceBibId, Integer sourceHoldingsId, List<ItemEntity> itemEntities, Integer destinationBibId, Integer destinationHoldingsId) {
        String message = "Item transferred from source (bibId : " + sourceBibId + " , holdingsId : " + sourceHoldingsId + ") to destination (bibId : " + destinationBibId + " , holdingsId : " + destinationHoldingsId + ")";
        helperUtil.saveItemChangeLogEntity(ScsbConstants.Transfer.TRANSFER_REQUEST, message, itemEntities);
    }

    class TransferValidationResponse {
        private boolean valid;
        private String message;
        private BibliographicEntity sourceBib;
        private BibliographicEntity destBib;
        private HoldingsEntity sourceHoldings;
        private HoldingsEntity destHoldings;
        private ItemEntity sourceItem;
        private String destinationBibId;
        private String destHoldingsId;

        public TransferValidationResponse(boolean valid) {
            this.valid = valid;
        }

        public TransferValidationResponse(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public BibliographicEntity getSourceBib() {
            return sourceBib;
        }

        public void setSourceBib(BibliographicEntity sourceBib) {
            this.sourceBib = sourceBib;
        }

        public BibliographicEntity getDestBib() {
            return destBib;
        }

        public void setDestBib(BibliographicEntity destBib) {
            this.destBib = destBib;
        }

        public HoldingsEntity getSourceHoldings() {
            return sourceHoldings;
        }

        public void setSourceHoldings(HoldingsEntity sourceHoldings) {
            this.sourceHoldings = sourceHoldings;
        }

        public String getDestinationBibId() {
            return destinationBibId;
        }

        public void setDestinationBibId(String destinationBibId) {
            this.destinationBibId = destinationBibId;
        }

        public HoldingsEntity getDestHoldings() {
            return destHoldings;
        }

        public void setDestHoldings(HoldingsEntity destHoldings) {
            this.destHoldings = destHoldings;
        }

        public ItemEntity getSourceItem() {
            return sourceItem;
        }

        public void setSourceItem(ItemEntity sourceItem) {
            this.sourceItem = sourceItem;
        }

        public String getDestHoldingsId() {
            return destHoldingsId;
        }

        public void setDestHoldingsId(String destHoldingsId) {
            this.destHoldingsId = destHoldingsId;
        }

        public void setInvalidWithMessage(String message) {
            this.setValid(false);
            this.setMessage(message);
        }
    }

    public InstitutionDetailsRepository getInstitutionDetailsRepository() {
        return institutionDetailsRepository;
    }
}
