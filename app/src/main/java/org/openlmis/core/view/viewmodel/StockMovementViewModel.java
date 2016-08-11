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

package org.openlmis.core.view.viewmodel;


import org.apache.commons.lang3.StringUtils;
import org.openlmis.core.LMISApp;
import org.openlmis.core.R;
import org.openlmis.core.exceptions.MovementReasonNotFoundException;
import org.openlmis.core.manager.MovementReasonManager;
import org.openlmis.core.model.LotMovementItem;
import org.openlmis.core.model.StockCard;
import org.openlmis.core.model.StockMovementItem;
import org.openlmis.core.utils.DateUtil;
import org.roboguice.shaded.goole.common.base.Function;
import org.roboguice.shaded.goole.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StockMovementViewModel {

    MovementReasonManager.MovementReason reason;

    String movementDate;
    String stockExistence;
    String documentNo;
    String signature;
    String requested;
    boolean isDraft = true;

    private HashMap<MovementReasonManager.MovementType, String> typeQuantityMap = new HashMap<>();

    List<LotMovementViewModel> lotMovementViewModelList = new ArrayList<>();

    public StockMovementViewModel(StockMovementItem item) {
        movementDate = DateUtil.formatDate(item.getMovementDate());
        documentNo = item.getDocumentNumber();
        stockExistence = String.valueOf(item.getStockOnHand());
        signature = item.getSignature();
        if (null == item.getRequested()) {
            requested = "";
        } else {
            requested = String.valueOf(item.getRequested());
        }
        isDraft = false;

        try {
            reason = MovementReasonManager.getInstance().queryByCode(item.getReason());
        } catch (MovementReasonNotFoundException e) {
            throw new RuntimeException("MovementReason Cannot be find " + e.getMessage());
        }

        typeQuantityMap.put(item.getMovementType(), String.valueOf(item.getMovementQuantity()));
    }

    public String getReceived() {
        return typeQuantityMap.get(MovementReasonManager.MovementType.RECEIVE);
    }

    public void setReceived(String received) {
        typeQuantityMap.put(MovementReasonManager.MovementType.RECEIVE, received);
    }

    public String getIssued() {
        return typeQuantityMap.get(MovementReasonManager.MovementType.ISSUE);
    }

    public void setIssued(String issued) {
        typeQuantityMap.put(MovementReasonManager.MovementType.ISSUE, issued);
    }

    public String getNegativeAdjustment() {
        return typeQuantityMap.get(MovementReasonManager.MovementType.NEGATIVE_ADJUST);
    }

    public void setNegativeAdjustment(String negativeAdjustment) {
        typeQuantityMap.put(MovementReasonManager.MovementType.NEGATIVE_ADJUST, negativeAdjustment);
    }

    public String getPositiveAdjustment() {
        return typeQuantityMap.get(MovementReasonManager.MovementType.POSITIVE_ADJUST);
    }

    public void setPositiveAdjustment(String positiveAdjustment) {
        typeQuantityMap.put(MovementReasonManager.MovementType.POSITIVE_ADJUST, positiveAdjustment);
    }

    public StockMovementItem convertViewToModel(StockCard stockCard) {
        StockMovementItem stockMovementItem = new StockMovementItem();
        stockMovementItem.setStockOnHand(Long.parseLong(getStockExistence()));

        stockMovementItem.setReason(reason.getCode());
        stockMovementItem.setDocumentNumber(getDocumentNo());
        stockMovementItem.setMovementType(reason.getMovementType());

        if (reason.getMovementType().equals(MovementReasonManager.MovementType.ISSUE) || reason.getMovementType().equals(MovementReasonManager.MovementType.NEGATIVE_ADJUST)) {
            Long movementQuantity = Long.parseLong(typeQuantityMap.get(reason.getMovementType()));
            stockMovementItem.setMovementQuantity(movementQuantity);
        }

        stockMovementItem.setRequested((null == requested || requested.isEmpty()) ? null : Long.valueOf(requested));

        stockMovementItem.setSignature(signature);

        stockMovementItem.setMovementDate(DateUtil.parseString(getMovementDate(), DateUtil.DEFAULT_DATE_FORMAT));

        stockMovementItem.setStockCard(stockCard);

        populateNewLotQuantities(stockMovementItem);

        return stockMovementItem;
    }

    private void populateNewLotQuantities(final StockMovementItem stockMovementItem) {
        if (!lotMovementViewModelList.isEmpty()) {
            long receiveQuantity = 0;

            stockMovementItem.setLotMovementItemListWrapper(FluentIterable.from(lotMovementViewModelList).transform(new Function<LotMovementViewModel, LotMovementItem>() {
                @Override
                public LotMovementItem apply(LotMovementViewModel lotMovementViewModel) {
                    LotMovementItem lotItem = lotMovementViewModel.convertViewToModel(stockMovementItem.getStockCard().getProduct());
                    lotItem.setStockMovementItem(stockMovementItem);
                    return lotItem;
                }
            }).toList());

            for (LotMovementViewModel lotMovementViewModel: lotMovementViewModelList) {
                receiveQuantity += Long.parseLong(lotMovementViewModel.getQuantity());
            }
            stockMovementItem.setMovementQuantity(receiveQuantity);
            stockMovementItem.setStockOnHand(receiveQuantity + stockMovementItem.getStockOnHand());
        }
    }

    public boolean validateEmpty() {
        return reason != null && StringUtils.isNoneEmpty(movementDate) && !allQuantitiesEmpty();
    }

    public boolean validateInputValid() {
        return (isAnyQuantitiesNumeric() && Long.parseLong(stockExistence) >= 0);
    }

    public boolean validateQuantitiesNotZero() {
        if (!StringUtils.isEmpty(getReceived())) {
            return Long.parseLong(getReceived()) > 0;
        } else if (!StringUtils.isEmpty(getIssued())) {
            return Long.parseLong(getIssued()) > 0;
        } else if (!StringUtils.isEmpty(getPositiveAdjustment())) {
            return Long.parseLong(getPositiveAdjustment()) > 0;
        } else if (!StringUtils.isEmpty(getNegativeAdjustment())) {
            return Long.parseLong(getNegativeAdjustment()) > 0;
        }
        return true;
    }

    public boolean isIssuedReason() {
        return getReason() != null && getReason().isIssueAdjustment();
    }

    private boolean allQuantitiesEmpty() {
        return StringUtils.isEmpty(getReceived())
                && StringUtils.isEmpty(getIssued())
                && StringUtils.isEmpty(getPositiveAdjustment())
                && StringUtils.isEmpty(getNegativeAdjustment());
    }

    private boolean isAnyQuantitiesNumeric() {
        return StringUtils.isNumeric(getReceived())
                || StringUtils.isNumeric(getNegativeAdjustment())
                || StringUtils.isNumeric(getPositiveAdjustment())
                || StringUtils.isNumeric(getIssued());
    }


    public void populateStockExistence(long previousStockOnHand) {
        MovementReasonManager.MovementType movementType = typeQuantityMap.keySet().iterator().next();

        //In RECEIVE lot management, no longer populate stock existence this way, populate later in the lot quantity aggregate
        if (LMISApp.getInstance().getFeatureToggleFor(R.bool.feature_lot_management)) {
            if (MovementReasonManager.MovementType.ISSUE.equals(movementType) || MovementReasonManager.MovementType.NEGATIVE_ADJUST.equals(movementType)) {
                this.stockExistence = "" + (previousStockOnHand - Long.parseLong(typeQuantityMap.get(movementType)));
            } else {
                this.stockExistence = "" + previousStockOnHand;
            }
        } else {
            if (MovementReasonManager.MovementType.RECEIVE.equals(movementType) || MovementReasonManager.MovementType.POSITIVE_ADJUST.equals(movementType)) {
                this.stockExistence = "" + (previousStockOnHand + Long.parseLong(typeQuantityMap.get(movementType)));
            } else {
                this.stockExistence = "" + (previousStockOnHand - Long.parseLong(typeQuantityMap.get(movementType)));
            }
        }
    }
}
