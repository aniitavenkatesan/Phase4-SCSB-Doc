package org.recap.util;

import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrInputDocument;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.model.camel.EmailPayLoad;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.CollectionGroupEntity;
import org.recap.model.jpa.ItemChangeLogEntity;
import org.recap.model.jpa.ItemEntity;
import org.recap.repository.jpa.BibliographicDetailsRepository;
import org.recap.repository.jpa.CollectionGroupDetailsRepository;
import org.recap.repository.jpa.HoldingsDetailsRepository;
import org.recap.repository.jpa.ItemChangeLogDetailsRepository;
import org.recap.repository.jpa.ItemDetailsRepository;
import org.recap.repository.jpa.UserDetailsRepository;
import org.recap.repository.solr.main.ItemCrudRepository;
import org.recap.service.SCSBService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by rajeshbabuk on 5/1/17.
 */
@Service
public class UpdateCgdUtil {

    private static final Logger logger = LoggerFactory.getLogger(UpdateCgdUtil.class);

    @Value("${" + PropertyKeyConstants.SOLR_PARENT_CORE + "}")
    private String solrCore;

    @Value("${" + PropertyKeyConstants.NONHOLDINGID_INSTITUTION + "}")
    private List<String> nonHoldingInstitutionList;

    @Autowired
    private BibliographicDetailsRepository bibliographicDetailsRepository;

    @Autowired
    private HoldingsDetailsRepository holdingsDetailsRepository;

    @Autowired
    private ItemDetailsRepository itemDetailsRepository;

    @Autowired
    private CollectionGroupDetailsRepository collectionGroupDetailsRepository;

    @Autowired
    private ItemChangeLogDetailsRepository itemChangeLogDetailsRepository;

    @Autowired
    private ItemCrudRepository itemSolrCrudRepository;

    @Resource
    private SolrTemplate solrTemplate;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private UserDetailsRepository userDetailsRepository;

    @Autowired
    private SCSBService scsbService;


    /**
     * This method updates cgd for item in both solr and database based on the given input parameters and sends email on successful cgd updation.
     *
     * @param itemBarcode                   the item barcode
     * @param owningInstitution             the owning institution
     * @param oldCollectionGroupDesignation the old collection group designation
     * @param newCollectionGroupDesignation the new collection group designation
     * @param cgdChangeNotes                the cgd change notes
     * @return the string
     */
    public String updateCGDForItem(String itemBarcode, String owningInstitution, String oldCollectionGroupDesignation, String newCollectionGroupDesignation, String cgdChangeNotes, String username) {
        String userName = StringUtils.isBlank(username) ? ScsbCommonConstants.GUEST : username;
        List<ItemEntity> itemEntities = new ArrayList<>();
        Date lastUpdatedDate = new Date();
        String cgdChangeLog = oldCollectionGroupDesignation + ScsbConstants.TO + newCollectionGroupDesignation;
        String userCode = userDetailsRepository.findInstitutionCodeByUserName(username);
        String itemCode = itemDetailsRepository.findInstitutionCodeByBarcode(itemBarcode);
        List<String> userRoles = userDetailsRepository.getUserRoles(userName);
        try {
            if (scsbService.validateUserRoles(userRoles,userCode,itemCode)) {
                updateCGDForItemInDB(itemBarcode, newCollectionGroupDesignation, userName, lastUpdatedDate);
                itemEntities = itemDetailsRepository.findByBarcode(itemBarcode);
                setCGDChangeLogToItemEntity(cgdChangeLog, itemEntities);
                updateCGDForItemInSolr(itemEntities);
                saveItemChangeLogEntity(itemEntities, userName, lastUpdatedDate, ScsbCommonConstants.UPDATE_CGD, cgdChangeNotes);
                sendEmail(itemBarcode, owningInstitution, oldCollectionGroupDesignation, newCollectionGroupDesignation, cgdChangeNotes);
                return ScsbCommonConstants.SUCCESS;
            } else {
                return ScsbConstants.FAILURE_UPDATE_CGD;
            }
        } catch (Exception e) {
            logger.error(ScsbCommonConstants.LOG_ERROR,e);
            return ScsbCommonConstants.FAILURE + "-" + e.getMessage();
        }
    }

    /**
     * This method updates cgd for item in database.
     *
     * @param itemBarcode                   the item barcode
     * @param newCollectionGroupDesignation the new collection group designation
     * @param username                      the username
     * @param lastUpdatedDate               the last updated date
     */
    public void updateCGDForItemInDB(String itemBarcode, String newCollectionGroupDesignation, String username, Date lastUpdatedDate) {
        CollectionGroupEntity collectionGroupEntity = collectionGroupDetailsRepository.findByCollectionGroupCode(newCollectionGroupDesignation);
        itemDetailsRepository.updateCollectionGroupIdByItemBarcode(collectionGroupEntity.getId(), itemBarcode, username, lastUpdatedDate);
    }

    /**
     * This method updates cgd for item in solr.
     *
     * @param itemEntities the item entities
     */
    public void updateCGDForItemInSolr(List<ItemEntity> itemEntities) {
        BibJSONUtil bibJSONUtil = new BibJSONUtil();
        bibJSONUtil.setNonHoldingInstitutions(nonHoldingInstitutionList);
        if (CollectionUtils.isNotEmpty(itemEntities)) {
            for (ItemEntity itemEntity : itemEntities) {
                if (itemEntity != null && CollectionUtils.isNotEmpty(itemEntity.getBibliographicEntities())) {
                    for (BibliographicEntity bibliographicEntity : itemEntity.getBibliographicEntities()) {
                        StopWatch stopWatchIndexDocument = new StopWatch();
                        stopWatchIndexDocument.start();
                        SolrInputDocument bibSolrInputDocument = bibJSONUtil.generateBibAndItemsForIndex(bibliographicEntity, solrTemplate, bibliographicDetailsRepository, holdingsDetailsRepository);
                        solrTemplate.saveDocument(solrCore, bibSolrInputDocument);
                        solrTemplate.commit(solrCore);
                        stopWatchIndexDocument.stop();
                        logger.info("Time taken to index the doc for updateCGDForItemInSolr--->{}sec",stopWatchIndexDocument.getTotalTimeSeconds());
                    }
                }
            }
        }
    }

    /**
     * This method is used to save the updated cgd in itemChangeLogEntity.
     * @param itemEntities
     * @param username
     * @param lastUpdatedDate
     * @param operationType
     * @param notes
     */
    private void saveItemChangeLogEntity(List<ItemEntity> itemEntities, String username, Date lastUpdatedDate, String operationType, String notes) {
        if (CollectionUtils.isNotEmpty(itemEntities)) {
            for (ItemEntity itemEntity : itemEntities) {
                ItemChangeLogEntity itemChangeLogEntity = new ItemChangeLogEntity();
                itemChangeLogEntity.setUpdatedBy(username);
                itemChangeLogEntity.setUpdatedDate(lastUpdatedDate);
                itemChangeLogEntity.setOperationType(operationType);
                itemChangeLogEntity.setRecordId(itemEntity.getId());
                itemChangeLogEntity.setNotes(notes);
                itemChangeLogDetailsRepository.save(itemChangeLogEntity);
            }
        }
    }

    /**
     * This method is used to send email using the email route builder.
     * @param itemBarcode
     * @param owningInstitution
     * @param oldCollectionGroupDesignation
     * @param newCollectionGroupDesignation
     * @param cgdChangeNotes
     */
    private void sendEmail(String itemBarcode, String owningInstitution, String oldCollectionGroupDesignation, String newCollectionGroupDesignation, String cgdChangeNotes) {
        EmailPayLoad emailPayLoad = new EmailPayLoad();
        emailPayLoad.setItemBarcode(itemBarcode);
        emailPayLoad.setItemInstitution(owningInstitution);
        emailPayLoad.setOldCgd(oldCollectionGroupDesignation);
        emailPayLoad.setNewCgd(newCollectionGroupDesignation);
        emailPayLoad.setNotes(cgdChangeNotes);
        producerTemplate.sendBodyAndHeader(ScsbConstants.EMAIL_Q, emailPayLoad, ScsbConstants.EMAIL_FOR, ScsbConstants.UPDATECGD);
    }

    /**
     * This method is used to set the cgdChangeLog in Item Entity.
     * @param cgdChangeLog
     * @param itemEntityList
     */
    private void setCGDChangeLogToItemEntity(String cgdChangeLog,List<ItemEntity> itemEntityList){
        for(ItemEntity itemEntity:itemEntityList){
            itemEntity.setCgdChangeLog(cgdChangeLog);
        }
    }
}
