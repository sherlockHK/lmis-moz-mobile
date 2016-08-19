package org.openlmis.core.model.repository;

import android.content.Context;

import com.google.inject.Inject;
import com.j256.ormlite.dao.Dao;

import org.openlmis.core.exceptions.LMISException;
import org.openlmis.core.model.Lot;
import org.openlmis.core.model.LotMovementItem;
import org.openlmis.core.model.LotOnHand;
import org.openlmis.core.persistence.DbUtil;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class LotRepository {

    @Inject
    DbUtil dbUtil;
    @Inject
    Context context;

    public void batchCreateLotsAndLotMovements(final List<LotMovementItem> lotMovementItemListWrapper) throws LMISException {
        try {
            dbUtil.withDaoAsBatch(LotMovementItem.class, new DbUtil.Operation<LotMovementItem, Object>() {
                @Override
                public LotMovementItem operate(Dao<LotMovementItem, String> dao) throws SQLException, LMISException {
                    for (final LotMovementItem lotMovementItem: lotMovementItemListWrapper) {
                        createOrUpdateLotAndLotOnHand(lotMovementItem);
                        createLotMovementItem(lotMovementItem);
                    }
                    return null;
                }
            });
        } catch (LMISException e) {
            e.reportToFabric();
        }
    }

    private void createOrUpdateLotAndLotOnHand(LotMovementItem lotMovementItem) throws LMISException {
        final Lot lot = lotMovementItem.getLot();
        Lot existingLot = getLotByLotNumberAndProductId(lot.getLotNumber(), lot.getProduct().getId());
        LotOnHand lotOnHand;

        if (existingLot == null) {
            lot.setCreatedAt(new Date());
            lot.setUpdatedAt(new Date());
            lotOnHand = new LotOnHand();
            lotOnHand.setLot(lot);
            lotOnHand.setStockCard(lotMovementItem.getStockMovementItem().getStockCard());
            dbUtil.withDao(Lot.class, new DbUtil.Operation<Lot, Void>() {
                @Override
                public Void operate(Dao<Lot, String> dao) throws SQLException {
                    dao.createOrUpdate(lot);
                    return null;
                }
            });
            lotOnHand.setQuantityOnHand(lotMovementItem.getMovementQuantity());
            final LotOnHand finalLotOnHand = lotOnHand;
            dbUtil.withDao(LotOnHand.class, new DbUtil.Operation<LotOnHand, Void>() {
                @Override
                public Void operate(Dao<LotOnHand, String> dao) throws SQLException {
                    dao.createOrUpdate(finalLotOnHand);
                    return null;
                }
            });
        } else {
            lotOnHand = getLotOnHandByLot(existingLot);
            lotOnHand.setQuantityOnHand(lotOnHand.getQuantityOnHand()+ lotMovementItem.getMovementQuantity());
            final LotOnHand finalLotOnHand = lotOnHand;
            dbUtil.withDao(LotOnHand.class, new DbUtil.Operation<LotOnHand, Void>() {
                @Override
                public Void operate(Dao<LotOnHand, String> dao) throws SQLException {
                    dao.createOrUpdate(finalLotOnHand);
                    return null;
                }
            });
        }
    }

    private void createLotMovementItem(final LotMovementItem lotMovementItem) throws LMISException {
        lotMovementItem.setCreatedAt(new Date());
        lotMovementItem.setUpdatedAt(new Date());
        lotMovementItem.setStockOnHand(lotMovementItem.getMovementQuantity());

        dbUtil.withDao(LotMovementItem.class, new DbUtil.Operation<LotMovementItem, Void>() {
            @Override
            public Void operate(Dao<LotMovementItem, String> dao) throws SQLException {
                dao.createOrUpdate(lotMovementItem);
                return null;
            }
        });
    }

    public LotOnHand getLotOnHandByLot(final Lot lot) throws LMISException {
        return dbUtil.withDao(LotOnHand.class, new DbUtil.Operation<LotOnHand, LotOnHand>() {
            @Override
            public LotOnHand operate(Dao<LotOnHand, String> dao) throws SQLException {
                return dao.queryBuilder()
                        .where()
                        .eq("lot_id", lot.getId())
                        .queryForFirst();
            }
        });
    }

    public Lot getLotByLotNumberAndProductId(final String lotNumber, final long productId) throws LMISException {
        return dbUtil.withDao(Lot.class, new DbUtil.Operation<Lot, Lot>() {
            @Override
            public Lot operate(Dao<Lot, String> dao) throws SQLException {
                return dao.queryBuilder()
                        .where()
                        .eq("lotNumber", lotNumber)
                        .and()
                        .eq("product_id", productId)
                        .queryForFirst();
            }
        });
    }
}