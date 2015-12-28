/*
 * This program is part of the OpenLMIS logistics management information
 * system platform software.
 *
 * Copyright © 2015 ThoughtWorks, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. This program is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details. You should
 * have received a copy of the GNU Affero General Public License along with
 * this program. If not, see http://www.gnu.org/licenses. For additional
 * information contact info@OpenLMIS.org
 */

package org.openlmis.core.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.openlmis.core.LMISApp;
import org.openlmis.core.R;
import org.openlmis.core.exceptions.LMISException;
import org.openlmis.core.exceptions.NoFacilityForUserException;
import org.openlmis.core.manager.SharedPreferenceMgr;
import org.openlmis.core.manager.UserInfoMgr;
import org.openlmis.core.model.StockCard;
import org.openlmis.core.model.User;
import org.openlmis.core.model.repository.ProgramRepository;
import org.openlmis.core.model.repository.RnrFormRepository;
import org.openlmis.core.model.repository.StockRepository;
import org.openlmis.core.network.LMISRestApi;
import org.openlmis.core.network.LMISRestManager;
import org.openlmis.core.network.model.SyncBackProductsResponse;
import org.openlmis.core.network.model.SyncDownRequisitionsResponse;
import org.openlmis.core.network.model.SyncDownStockCardResponse;
import org.openlmis.core.utils.DateUtil;

import java.util.Date;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

@Singleton
public class SyncDownManager {
    private static final int DAYS_OF_MONTH = 30;
    private static final int MONTHS_OF_YEAR = 12;

    private boolean isSyncing = false;

    protected LMISRestApi lmisRestApi;

    @Inject
    SharedPreferenceMgr sharedPreferenceMgr;
    @Inject
    RnrFormRepository rnrFormRepository;
    @Inject
    StockRepository stockRepository;
    @Inject
    ProgramRepository programRepository;

    public SyncDownManager() {
        lmisRestApi = new LMISRestManager().getLmisRestApi();
    }

    public void syncBackServerData(Subscriber<SyncProgress> subscriber) {
        if (isSyncing) {
            subscriber.onCompleted();
            return;
        }

        isSyncing = true;
        Observable.create(new Observable.OnSubscribe<SyncProgress>() {
            @Override
            public void call(Subscriber<? super SyncProgress> subscriber) {
                try {
                    syncProducts(subscriber);
                    syncBackLastMonthStockCards(subscriber);
                    syncBackRequisition(subscriber);
                    syncLastYearStockCardsSilently(subscriber);

                    isSyncing = false;
                    subscriber.onCompleted();
                } catch (LMISException e) {
                    e.reportToFabric();
                    isSyncing = false;
                    subscriber.onError(e);
                }
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(subscriber);
    }

    private void syncLastYearStockCardsSilently(Subscriber<? super SyncProgress> subscriber) {
        if (sharedPreferenceMgr.shouldSyncLastYearStockData()) {
            try {
                subscriber.onNext(SyncProgress.SyncingStockCardsLastYear);
                fetchLatestYearStockMovements();
                sharedPreferenceMgr.setShouldSyncLastYearStockCardData(false);
                subscriber.onNext(SyncProgress.StockCardsLastYearSynced);
            } catch (LMISException e) {
                sharedPreferenceMgr.setShouldSyncLastYearStockCardData(true);
                e.reportToFabric();
            }
        }
    }

    private void syncBackRequisition(Subscriber<? super SyncProgress> subscriber) throws LMISException {
        if (!sharedPreferenceMgr.isRequisitionDataSynced()) {
            try {
                subscriber.onNext(SyncProgress.SyncingRequisition);
                fetchAndSaveRequisition();
                sharedPreferenceMgr.setRequisitionDataSynced(true);
                subscriber.onNext(SyncProgress.RequisitionSynced);
            } catch (LMISException e) {
                sharedPreferenceMgr.setRequisitionDataSynced(false);
                throw new LMISException(errorMessage(R.string.msg_sync_requisition_failed));
            }
        }
    }

    private void syncBackLastMonthStockCards(Subscriber<? super SyncProgress> subscriber) throws LMISException {
        if (!sharedPreferenceMgr.isLastMonthStockDataSynced()) {
            try {
                subscriber.onNext(SyncProgress.SyncingStockCardsLastMonth);
                fetchLatestOneMonthMovements();
                sharedPreferenceMgr.setLastMonthStockCardDataSynced(true);
                sharedPreferenceMgr.setShouldSyncLastYearStockCardData(true);
                subscriber.onNext(SyncProgress.StockCardsLastMonthSynced);
            } catch (LMISException e) {
                sharedPreferenceMgr.setLastMonthStockCardDataSynced(false);
                throw new LMISException(errorMessage(R.string.msg_sync_stockmovement_failed));
            }
        }
    }

    private void syncProducts(Subscriber<? super SyncProgress> subscriber) throws LMISException {
        if (!sharedPreferenceMgr.hasGetProducts()) {
            try {
                subscriber.onNext(SyncProgress.SyncingProduct);
                fetchAndSaveProductsWithProgram();
                sharedPreferenceMgr.setHasGetProducts(true);
                subscriber.onNext(SyncProgress.ProductSynced);
            } catch (LMISException e) {
                sharedPreferenceMgr.setHasGetProducts(false);
                throw e;
            }
        }
    }

    private void fetchAndSaveProductsWithProgram() throws LMISException {
        User user = UserInfoMgr.getInstance().getUser();
        if (StringUtils.isEmpty(user.getFacilityCode())) {
            throw new NoFacilityForUserException(errorMessage(R.string.msg_user_not_facility));
        }
        try {
            SyncBackProductsResponse response = lmisRestApi.fetchProducts(user.getFacilityCode());
            programRepository.saveProgramWithProduct(response.getProgramsWithProducts());
        } catch (Exception e) {
            throw new LMISException(errorMessage(R.string.msg_sync_products_list_failed));
        }
    }

    private void fetchAndSaveStockCards(String startDate, String endDate) throws LMISException {
        //default start date is one month before and end date is one day after
        final String facilityId = UserInfoMgr.getInstance().getUser().getFacilityId();

        SyncDownStockCardResponse syncDownStockCardResponse = lmisRestApi.fetchStockMovementData(facilityId, startDate, endDate);

        for (StockCard stockCard : syncDownStockCardResponse.getStockCards()) {
            if (stockCard.getId() <= 0) {
                stockRepository.saveStockCardAndBatchUpdateMovements(stockCard);
            } else {
                stockRepository.batchCreateOrUpdateStockMovements(stockCard.getStockMovementItemsWrapper());
            }
        }
    }

    private void fetchAndSaveRequisition() throws LMISException {
        SyncDownRequisitionsResponse syncDownRequisitionsResponse = lmisRestApi.fetchRequisitions(UserInfoMgr.getInstance().getUser().getFacilityCode());

        if (syncDownRequisitionsResponse == null) {
            throw new LMISException("Can't get SyncDownRequisitionsResponse, you can check json parse to POJO logic");
        }

        rnrFormRepository.createFormAndItems(syncDownRequisitionsResponse.getRequisitions());
    }

    private void fetchLatestOneMonthMovements() throws LMISException {
        Date now = new Date();
        Date startDate = DateUtil.minusDayOfMonth(now, DAYS_OF_MONTH);
        String startDateStr = DateUtil.formatDate(startDate, "yyyy-MM-dd");

        Date endDate = DateUtil.addDayOfMonth(now, 1);
        String endDateStr = DateUtil.formatDate(endDate, "yyyy-MM-dd");
        fetchAndSaveStockCards(startDateStr, endDateStr);

        List<StockCard> syncedStockCard = stockRepository.list();
        if (!(syncedStockCard == null || syncedStockCard.isEmpty())) {
            sharedPreferenceMgr.setIsNeedsInventory(false);
        }
    }

    private void fetchLatestYearStockMovements() throws LMISException {
        long syncEndTimeMillions = sharedPreferenceMgr.getPreference().getLong(SharedPreferenceMgr.KEY_STOCK_SYNC_END_TIME, new Date().getTime());

        Date now = new Date(syncEndTimeMillions);

        int startMonth = sharedPreferenceMgr.getPreference().getInt(SharedPreferenceMgr.KEY_STOCK_SYNC_CURRENT_INDEX, 1);

        for (int month = startMonth; month <= MONTHS_OF_YEAR; month++) {
            Date startDate = DateUtil.minusDayOfMonth(now, DAYS_OF_MONTH * (month + 1));
            String startDateStr = DateUtil.formatDate(startDate, "yyyy-MM-dd");

            Date endDate = DateUtil.minusDayOfMonth(now, DAYS_OF_MONTH * month);
            String endDateStr = DateUtil.formatDate(endDate, "yyyy-MM-dd");

            try {
                fetchAndSaveStockCards(startDateStr, endDateStr);
            } catch (LMISException e) {
                sharedPreferenceMgr.getPreference().edit().putLong(SharedPreferenceMgr.KEY_STOCK_SYNC_END_TIME, syncEndTimeMillions).apply();
                sharedPreferenceMgr.getPreference().edit().putInt(SharedPreferenceMgr.KEY_STOCK_SYNC_CURRENT_INDEX, month).apply();
                throw e;
            }
        }
    }

    private String errorMessage(int code) {
        return LMISApp.getContext().getResources().getString(code);
    }

    public enum SyncProgress {
        SyncingProduct(R.string.msg_fetching_products),
        SyncingStockCardsLastMonth(R.string.msg_sync_stock_movements_data),
        SyncingRequisition(R.string.msg_sync_requisition_data),
        SyncingStockCardsLastYear,

        ProductSynced,
        StockCardsLastMonthSynced,
        RequisitionSynced,
        StockCardsLastYearSynced;

        private int messageCode;

        SyncProgress(int messageCode) {
            this.messageCode = messageCode;
        }

        SyncProgress() {
        }

        public int getMessageCode() {
            return messageCode;
        }
    }
}