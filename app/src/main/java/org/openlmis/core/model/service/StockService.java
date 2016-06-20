package org.openlmis.core.model.service;

import com.google.inject.Inject;

import org.joda.time.DateTime;
import org.openlmis.core.exceptions.LMISException;
import org.openlmis.core.exceptions.StockMovementIsNullException;
import org.openlmis.core.manager.SharedPreferenceMgr;
import org.openlmis.core.model.Cmm;
import org.openlmis.core.model.Period;
import org.openlmis.core.model.StockCard;
import org.openlmis.core.model.StockMovementItem;
import org.openlmis.core.model.repository.CmmRepository;
import org.openlmis.core.model.repository.StockRepository;
import org.openlmis.core.utils.DateUtil;
import org.roboguice.shaded.goole.common.base.Optional;
import org.roboguice.shaded.goole.common.base.Predicate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.openlmis.core.utils.DateUtil.today;
import static org.roboguice.shaded.goole.common.collect.FluentIterable.from;

public class StockService {

    private final int LOW_STOCK_CALCULATE_MONTH_QUANTITY = 3;

    @Inject
    StockRepository stockRepository;
    @Inject
    CmmRepository cmmRepository;

    public StockService() {
    }

    protected Date queryFirstPeriodBegin(final StockCard stockCard) throws LMISException {
        StockMovementItem stockMovementItem = stockRepository.queryFirstStockMovementItem(stockCard);
        if (stockMovementItem == null) {
            throw new StockMovementIsNullException(stockCard);
        }
        return stockMovementItem.getMovementPeriod().getBegin().toDate();
    }

    public void monthlyUpdateAvgMonthlyConsumption() {
        DateTime recordLowStockAvgPeriod = SharedPreferenceMgr.getInstance().getLatestUpdateLowStockAvgTime();
        Period period = Period.of(today());
        if (recordLowStockAvgPeriod.isBefore(period.getBegin())) {
            immediatelyUpdateAvgMonthlyConsumption();
        }
    }

    public void immediatelyUpdateAvgMonthlyConsumption() {
        try {
            List<StockCard> stockCards = stockRepository.list();
            for (StockCard stockCard : stockCards) {
                stockCard.setAvgMonthlyConsumption(calculateAverageMonthlyConsumption(stockCard));
                stockRepository.createOrUpdate(stockCard);
                cmmRepository.save(Cmm.initWith(stockCard, Period.of(today())));
            }
            SharedPreferenceMgr.getInstance().updateLatestLowStockAvgTime();
        } catch (LMISException e) {
            e.reportToFabric();
        }
    }

    protected float calculateAverageMonthlyConsumption(StockCard stockCard) {
        Date firstPeriodBegin;
        try {
            firstPeriodBegin = queryFirstPeriodBegin(stockCard);
        } catch (LMISException e) {
            e.reportToFabric();
            return -1;
        }

        List<Long> issuePerMonths = new ArrayList<>();
        Period period = Period.of(today());
        int periodQuantity = DateUtil.calculateDateMonthOffset(firstPeriodBegin, period.getBegin().toDate());

        if (periodQuantity < LOW_STOCK_CALCULATE_MONTH_QUANTITY) {
            return -1;
        }

        for (int i = 0; i < periodQuantity; i++) {
            period = period.previous();
            Long totalIssuesEachMonth = calculateTotalIssuesPerPeriod(stockCard, period);

            if (totalIssuesEachMonth == null) {
                continue;
            }

            issuePerMonths.add(totalIssuesEachMonth);

            if (issuePerMonths.size() == LOW_STOCK_CALCULATE_MONTH_QUANTITY) {
                break;
            }
        }

        if (issuePerMonths.size() < LOW_STOCK_CALCULATE_MONTH_QUANTITY) {
            return -1;
        }
        return getTotalIssues(issuePerMonths) * 1f / LOW_STOCK_CALCULATE_MONTH_QUANTITY;
    }

    private long getTotalIssues(List<Long> issuePerMonths) {
        long total = 0;
        for (Long totalIssues : issuePerMonths) {
            total += totalIssues;
        }
        return total;
    }

    private Long calculateTotalIssuesPerPeriod(StockCard stockCard, Period period) {
        long totalIssued = 0;
        try {
            List<StockMovementItem> stockMovementItems = stockRepository
                    .queryStockItems(stockCard, period.getBegin().toDate(), period.getEnd().toDate());
            //the query above is actually wasteful, the movement items have already been queried and associated to the stock card

            boolean periodHasStockOut = periodHasStockOut(stockCard, stockMovementItems, period);
            if (periodHasStockOut) {
                return null;
            }

            for (StockMovementItem item : stockMovementItems) {
                if (StockMovementItem.MovementType.ISSUE == item.getMovementType()) {
                    totalIssued += item.getMovementQuantity();
                }
            }
            return totalIssued;
        } catch (LMISException e) {
            e.reportToFabric();
            return null;
        }
    }

    private boolean periodHasStockOut(StockCard stockCard, List<StockMovementItem> stockMovementItems, final Period period) {
        if (stockMovementItems.isEmpty()) {
            Optional<StockMovementItem> lastMovementBeforePeriod = from(stockCard.getStockMovementItemsWrapper()).filter(new Predicate<StockMovementItem>() {
                @Override
                public boolean apply(StockMovementItem stockMovementItem) {
                    return new DateTime(stockMovementItem.getMovementDate()).isBefore(period.getBegin());
                }
            }).last();

            boolean isStockOutStatusInherited = lastMovementBeforePeriod.isPresent() && lastMovementBeforePeriod.get().getStockOnHand() == 0;
            return isStockOutStatusInherited;//this boolean variable exists to explain its meaning, do not inline
        } else {
            boolean hasStockOutEventInThisPeriod = from(stockMovementItems).anyMatch(new Predicate<StockMovementItem>() {
                @Override
                public boolean apply(StockMovementItem stockMovementItem) {
                    return stockMovementItem.getStockOnHand() == 0;
                }
            });

            return hasStockOutEventInThisPeriod;//this boolean variable exists to explain its meaning, do not inline
        }
    }
}
