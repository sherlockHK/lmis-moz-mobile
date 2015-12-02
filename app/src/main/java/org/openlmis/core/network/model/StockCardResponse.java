package org.openlmis.core.network.model;

import org.openlmis.core.model.StockCard;

import java.util.List;

import lombok.Data;

@Data
public class StockCardResponse {
    List<StockCard> stockCards;
}

