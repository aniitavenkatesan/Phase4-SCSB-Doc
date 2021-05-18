package org.recap.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbCommonConstants;
import org.recap.model.jpa.BibliographicEntity;
import org.recap.model.jpa.CollectionGroupEntity;
import org.recap.model.jpa.HoldingsEntity;
import org.recap.model.jpa.InstitutionEntity;
import org.recap.model.jpa.ItemStatusEntity;
import org.recap.model.search.resolver.BibValueResolver;
import org.recap.model.search.resolver.HoldingsValueResolver;
import org.recap.model.search.resolver.ItemValueResolver;
import org.recap.model.search.resolver.impl.bib.TitleSubFieldAValueResolver;
import org.recap.model.search.resolver.impl.bib.AuthorDisplayValueResolver;
import org.recap.model.search.resolver.impl.bib.AuthorSearchValueResolver;
import org.recap.model.search.resolver.impl.bib.BibCreatedDateValueResolver;
import org.recap.model.search.resolver.impl.bib.BibIdValueResolver;
import org.recap.model.search.resolver.impl.bib.ISBNValueResolver;
import org.recap.model.search.resolver.impl.bib.ISSNValueResolver;
import org.recap.model.search.resolver.impl.bib.ImprintValueResolver;
import org.recap.model.search.resolver.impl.bib.IsDeletedBibValueResolver;
import org.recap.model.search.resolver.impl.bib.LCCNValueResolver;
import org.recap.model.search.resolver.impl.bib.LeaderMaterialTypeValueResolver;
import org.recap.model.search.resolver.impl.bib.MaterialTypeValueResolver;
import org.recap.model.search.resolver.impl.bib.NotesValueResolver;
import org.recap.model.search.resolver.impl.bib.OCLCValueResolver;
import org.recap.model.search.resolver.impl.bib.OwningInstitutionBibIdValueResolver;
import org.recap.model.search.resolver.impl.bib.OwningInstitutionValueResolver;
import org.recap.model.search.resolver.impl.bib.PublicationDateValueResolver;
import org.recap.model.search.resolver.impl.bib.PublicationPlaceValueResolver;
import org.recap.model.search.resolver.impl.bib.PublisherValueResolver;
import org.recap.model.search.resolver.impl.bib.RootValueResolver;
import org.recap.model.search.resolver.impl.bib.SubjectValueResolver;
import org.recap.model.search.resolver.impl.bib.TitleDisplayValueResolver;
import org.recap.model.search.resolver.impl.bib.TitleSearchValueResolver;
import org.recap.model.search.resolver.impl.bib.TitleSortValueResolver;
import org.recap.model.search.resolver.impl.holdings.HoldingsIdValueResolver;
import org.recap.model.search.resolver.impl.holdings.HoldingsRootValueResolver;
import org.recap.model.search.resolver.impl.holdings.IsDeletedHoldingsValueResolver;
import org.recap.model.search.resolver.impl.holdings.OwningInstitutionHoldingsIdValueResolver;
import org.recap.model.search.resolver.impl.holdings.SummaryHoldingsValueResolver;
import org.recap.model.search.resolver.impl.item.AvailabilityDisplayValueResolver;
import org.recap.model.search.resolver.impl.item.AvailabilitySearchValueResolver;
import org.recap.model.search.resolver.impl.item.BarcodeValueResolver;
import org.recap.model.search.resolver.impl.item.CallNumberDisplayValueResolver;
import org.recap.model.search.resolver.impl.item.CallNumberSearchValueResolver;
import org.recap.model.search.resolver.impl.item.CollectionGroupDesignationValueResolver;
import org.recap.model.search.resolver.impl.item.CustomerCodeValueResolver;
import org.recap.model.search.resolver.impl.item.HoldingsIdsValueResolver;
import org.recap.model.search.resolver.impl.item.IsDeletedItemValueResolver;
import org.recap.model.search.resolver.impl.item.ItemBibIdValueResolver;
import org.recap.model.search.resolver.impl.item.ItemCreatedDateValueResolver;
import org.recap.model.search.resolver.impl.item.ItemIdValueResolver;
import org.recap.model.search.resolver.impl.item.ItemLastUpdatedByValueResolver;
import org.recap.model.search.resolver.impl.item.ItemLastUpdatedDateValueResolver;
import org.recap.model.search.resolver.impl.item.ItemOwningInstitutionValueResolver;
import org.recap.model.search.resolver.impl.item.ItemRootValueResolver;
import org.recap.model.search.resolver.impl.item.OwningInstitutionItemIdValueResolver;
import org.recap.model.search.resolver.impl.item.UseRestrictionDisplayValueResolver;
import org.recap.model.search.resolver.impl.item.UseRestrictionSearchValueResolver;
import org.recap.model.search.resolver.impl.item.VolumePartYearValueResolver;
import org.recap.model.search.resolver.impl.item.ImsLocationValueResolver;
import org.recap.model.solr.BibItem;
import org.recap.model.solr.Item;
import org.recap.repository.jpa.CollectionGroupDetailsRepository;
import org.recap.repository.jpa.InstitutionDetailsRepository;
import org.recap.repository.jpa.ItemStatusDetailsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommonUtil {

    private static final Logger logger = LoggerFactory.getLogger(CommonUtil.class);

    private List<BibValueResolver> bibValueResolvers;
    private List<HoldingsValueResolver> holdingsValueResolvers;
    private List<ItemValueResolver> itemValueResolvers;
    private Map institutionEntityMap;
    private Map itemStatusMap;
    private Map collectionGroupMap;

    @Autowired
    private InstitutionDetailsRepository institutionDetailsRepository;

    @Autowired
    private ItemStatusDetailsRepository itemStatusDetailsRepository;

    @Autowired
    private CollectionGroupDetailsRepository collectionGroupDetailsRepository;

    @Value("${" + PropertyKeyConstants.SCSB_SUPPORT_INSTITUTION + "}")
    private String supportInstitution;

    /**
     * This method gets item for the given item solr document.
     *
     * @param itemSolrDocument the item solr document
     * @return the item
     */
    public Item getItem(SolrDocument itemSolrDocument) {
        Item item = new Item();
        Collection<String> fieldNames = itemSolrDocument.getFieldNames();
        itemValueResolvers = getItemValueResolvers();
        for (Iterator<String> iterator = fieldNames.iterator(); iterator.hasNext(); ) {
            String fieldName = iterator.next();
            Object fieldValue = itemSolrDocument.getFieldValue(fieldName);
            for (Iterator<ItemValueResolver> itemValueResolverIterator = itemValueResolvers.iterator(); itemValueResolverIterator.hasNext(); ) {
                ItemValueResolver itemValueResolver = itemValueResolverIterator.next();
                if (itemValueResolver.isInterested(fieldName).booleanValue()) {
                    itemValueResolver.setValue(item, fieldValue);
                }
            }
        }
        return item;
    }

    /**
     * Gets list of bib value resolvers which is used to set appropriated values in bib .
     *
     * @return the bib value resolvers
     */
    public List<BibValueResolver> getBibValueResolvers() {
        if (null == bibValueResolvers) {
            bibValueResolvers = new ArrayList<>();
            bibValueResolvers.add(new RootValueResolver());
            bibValueResolvers.add(new AuthorDisplayValueResolver());
            bibValueResolvers.add(new AuthorSearchValueResolver());
            bibValueResolvers.add(new BibIdValueResolver());
            bibValueResolvers.add(new org.recap.model.search.resolver.impl.bib.DocTypeValueResolver());
            bibValueResolvers.add(new org.recap.model.search.resolver.impl.bib.IdValueResolver());
            bibValueResolvers.add(new ImprintValueResolver());
            bibValueResolvers.add(new ISBNValueResolver());
            bibValueResolvers.add(new ISSNValueResolver());
            bibValueResolvers.add(new LCCNValueResolver());
            bibValueResolvers.add(new LeaderMaterialTypeValueResolver());
            bibValueResolvers.add(new MaterialTypeValueResolver());
            bibValueResolvers.add(new NotesValueResolver());
            bibValueResolvers.add(new OCLCValueResolver());
            bibValueResolvers.add(new OwningInstitutionBibIdValueResolver());
            bibValueResolvers.add(new OwningInstitutionValueResolver());
            bibValueResolvers.add(new PublicationDateValueResolver());
            bibValueResolvers.add(new PublicationPlaceValueResolver());
            bibValueResolvers.add(new PublisherValueResolver());
            bibValueResolvers.add(new SubjectValueResolver());
            bibValueResolvers.add(new TitleDisplayValueResolver());
            bibValueResolvers.add(new TitleSearchValueResolver());
            bibValueResolvers.add(new TitleSortValueResolver());
            bibValueResolvers.add(new TitleSubFieldAValueResolver());
            bibValueResolvers.add(new IsDeletedBibValueResolver());
            bibValueResolvers.add(new BibCreatedDateValueResolver());
        }
        return bibValueResolvers;
    }

    /**
     * Gets list of holdings value resolvers which is used to set appropriated values in holdings.
     *
     * @return the holdings value resolvers
     */
    public List<HoldingsValueResolver> getHoldingsValueResolvers() {
        if(null == holdingsValueResolvers) {
            holdingsValueResolvers = new ArrayList<>();
            holdingsValueResolvers.add(new HoldingsRootValueResolver());
            holdingsValueResolvers.add(new SummaryHoldingsValueResolver());
            holdingsValueResolvers.add(new org.recap.model.search.resolver.impl.holdings.DocTypeValueResolver());
            holdingsValueResolvers.add(new org.recap.model.search.resolver.impl.holdings.IdValueResolver());
            holdingsValueResolvers.add(new HoldingsIdValueResolver());
            holdingsValueResolvers.add(new IsDeletedHoldingsValueResolver());
            holdingsValueResolvers.add(new OwningInstitutionHoldingsIdValueResolver());
        }
        return holdingsValueResolvers;
    }

    /**
     * Gets list of item value resolvers which is used to set appropriated values in item.
     *
     * @return the item value resolvers
     */
    public List<ItemValueResolver> getItemValueResolvers() {
        if (null == itemValueResolvers) {
            itemValueResolvers = new ArrayList<>();
            itemValueResolvers.add(new AvailabilitySearchValueResolver());
            itemValueResolvers.add(new AvailabilityDisplayValueResolver());
            itemValueResolvers.add(new BarcodeValueResolver());
            itemValueResolvers.add(new CallNumberSearchValueResolver());
            itemValueResolvers.add(new CallNumberDisplayValueResolver());
            itemValueResolvers.add(new CollectionGroupDesignationValueResolver());
            itemValueResolvers.add(new CustomerCodeValueResolver());
            itemValueResolvers.add(new org.recap.model.search.resolver.impl.item.DocTypeValueResolver());
            itemValueResolvers.add(new ItemOwningInstitutionValueResolver());
            itemValueResolvers.add(new UseRestrictionSearchValueResolver());
            itemValueResolvers.add(new UseRestrictionDisplayValueResolver());
            itemValueResolvers.add(new VolumePartYearValueResolver());
            itemValueResolvers.add(new ItemRootValueResolver());
            itemValueResolvers.add(new ItemIdValueResolver());
            itemValueResolvers.add(new org.recap.model.search.resolver.impl.item.IdValueResolver());
            itemValueResolvers.add(new IsDeletedItemValueResolver());
            itemValueResolvers.add(new ItemCreatedDateValueResolver());
            itemValueResolvers.add(new OwningInstitutionItemIdValueResolver());
            itemValueResolvers.add(new ItemLastUpdatedDateValueResolver());
            itemValueResolvers.add(new ItemLastUpdatedByValueResolver());
            itemValueResolvers.add(new ItemBibIdValueResolver());
            itemValueResolvers.add(new HoldingsIdsValueResolver());
            itemValueResolvers.add(new ImsLocationValueResolver());
        }
        return itemValueResolvers;
    }

    /**
     * This method builds Holdings Entity from holdings content
     * @param bibliographicEntity
     * @param currentDate
     * @param errorMessage
     * @param holdingsContent
     * @return
     */
    public HoldingsEntity buildHoldingsEntity(BibliographicEntity bibliographicEntity, Date currentDate, StringBuilder errorMessage, String holdingsContent) {
        HoldingsEntity holdingsEntity = new HoldingsEntity();
        if (StringUtils.isNotBlank(holdingsContent)) {
            holdingsEntity.setContent(holdingsContent.getBytes());
        } else {
            errorMessage.append("Holdings Content cannot be empty").append(",");
        }
        holdingsEntity.setDeleted(false);
        holdingsEntity.setCreatedDate(currentDate);
        holdingsEntity.setCreatedBy(ScsbCommonConstants.ACCESSION);
        holdingsEntity.setLastUpdatedDate(currentDate);
        holdingsEntity.setLastUpdatedBy(ScsbCommonConstants.ACCESSION);
        Integer owningInstitutionId = bibliographicEntity.getOwningInstitutionId();
        holdingsEntity.setOwningInstitutionId(owningInstitutionId);
        return holdingsEntity;
    }

    /**
     * Add Holdings Entity to Map
     * @param map
     * @param holdingsEntity
     * @param owningInstitutionHoldingsId
     * @return
     */
    public Map<String, Object> addHoldingsEntityToMap(Map<String, Object> map, HoldingsEntity holdingsEntity, String owningInstitutionHoldingsId) {
        if (StringUtils.isBlank(owningInstitutionHoldingsId) || owningInstitutionHoldingsId.length() > 100) {
            owningInstitutionHoldingsId = UUID.randomUUID().toString();
        }
        holdingsEntity.setOwningInstitutionHoldingsId(owningInstitutionHoldingsId);
        map.put("holdingsEntity", holdingsEntity);
        return map;
    }

    /**
     * Builds BibItem object from Solr Document Field Names
     * @param solrDocument
     * @param fieldNames
     * @return
     */
    public BibItem getBibItemFromSolrFieldNames(SolrDocument solrDocument, Collection<String> fieldNames, BibItem bibItem) {
        for (Iterator<String> stringIterator = fieldNames.iterator(); stringIterator.hasNext(); ) {
            String fieldName = stringIterator.next();
            Object fieldValue = solrDocument.getFieldValue(fieldName);
            for (Iterator<BibValueResolver> valueResolverIterator = getBibValueResolvers().iterator(); valueResolverIterator.hasNext(); ) {
                BibValueResolver valueResolver = valueResolverIterator.next();
                if (valueResolver.isInterested(fieldName).booleanValue()) {
                    valueResolver.setValue(bibItem, fieldValue);
                }
            }
        }
        return bibItem;
    }

    /**
     * This method gets all institution entity and puts it in a map.
     *
     * @return the institution entity map
     */
    public Map getInstitutionEntityMap() {
        if (null == institutionEntityMap) {
            institutionEntityMap = new HashMap();
            try {
                Iterable<InstitutionEntity> institutionEntities = institutionDetailsRepository.findAll();
                for (Iterator iterator = institutionEntities.iterator(); iterator.hasNext(); ) {
                    InstitutionEntity institutionEntity = (InstitutionEntity) iterator.next();
                    institutionEntityMap.put(institutionEntity.getInstitutionCode(), institutionEntity.getId());
                }
            } catch (Exception e) {
                logger.error(ScsbCommonConstants.LOG_ERROR,e);
            }
        }
        return institutionEntityMap;
    }

    /**
     * This method gets all item status and puts it in a map.
     *
     * @return the item status map
     */
    public Map getItemStatusMap() {
        if (null == itemStatusMap) {
            itemStatusMap = new HashMap();
            try {
                Iterable<ItemStatusEntity> itemStatusEntities = itemStatusDetailsRepository.findAll();
                for (Iterator iterator = itemStatusEntities.iterator(); iterator.hasNext(); ) {
                    ItemStatusEntity itemStatusEntity = (ItemStatusEntity) iterator.next();
                    itemStatusMap.put(itemStatusEntity.getStatusCode(), itemStatusEntity.getId());
                }
            } catch (Exception e) {
                logger.error(ScsbCommonConstants.LOG_ERROR,e);
            }
        }
        return itemStatusMap;
    }

    /**
     * This method gets all collection group and puts it in a map.
     *
     * @return the collection group map
     */
    public Map getCollectionGroupMap() {
        if (null == collectionGroupMap) {
            collectionGroupMap = new HashMap();
            try {
                Iterable<CollectionGroupEntity> collectionGroupEntities = collectionGroupDetailsRepository.findAll();
                for (Iterator iterator = collectionGroupEntities.iterator(); iterator.hasNext(); ) {
                    CollectionGroupEntity collectionGroupEntity = (CollectionGroupEntity) iterator.next();
                    collectionGroupMap.put(collectionGroupEntity.getCollectionGroupCode(), collectionGroupEntity.getId());
                }
            } catch (Exception e) {
                logger.error(ScsbCommonConstants.LOG_ERROR,e);
            }
        }
        return collectionGroupMap;
    }

    /**
     * Build Solr Documents for Query By Doc Type
     * @param solrQueryForDocType
     * @param solrTemplate
     * @return
     * @throws SolrServerException
     * @throws IOException
     */
    public SolrDocumentList getSolrDocumentsByDocType(SolrQuery solrQueryForDocType, SolrTemplate solrTemplate) throws SolrServerException, IOException {
        QueryResponse queryResponse;
        queryResponse = solrTemplate.getSolrClient().query(solrQueryForDocType);
        SolrDocumentList solrDocuments = queryResponse.getResults();
        if (solrDocuments.getNumFound() > 10) {
            solrQueryForDocType.setRows((int) solrDocuments.getNumFound());
            queryResponse = solrTemplate.getSolrClient().query(solrQueryForDocType);
            solrDocuments = queryResponse.getResults();
        }
        return solrDocuments;
    }

    public List<String> getAllInstitutionCodes() {
        List<String> institutionCodes = new ArrayList<>();
        try {
            Iterable<InstitutionEntity> institutionEntities = institutionDetailsRepository.findAll();
            for (Iterator iterator = institutionEntities.iterator(); iterator.hasNext(); ) {
                InstitutionEntity institutionEntity = (InstitutionEntity) iterator.next();
                institutionCodes.add(institutionEntity.getInstitutionCode());
            }
        } catch (Exception e) {
            logger.error(ScsbCommonConstants.LOG_ERROR,e);
        }
        return institutionCodes;
    }

    /**
     * Get All Institution Codes Except Support Institution
     * @return institutionCodes
     */
    public List<String> findAllInstitutionCodesExceptSupportInstitution() {
        return institutionDetailsRepository.findAllInstitutionCodesExceptSupportInstitution(supportInstitution);
    }

    /**
     * Get All Institution Codes Except Support Institution
     * @return institutionCodes
     */
    public List<InstitutionEntity> findAllInstitutionsExceptSupportInstitution() {
        return institutionDetailsRepository.findAllInstitutionsExceptSupportInstitution(supportInstitution);
    }

}
