package datasources.taxamo;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.taxamo.client.model.Transactions;
import org.apache.commons.lang3.StringUtils;
import usecases.models.Billing;
import usecases.models.Transaction;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

class BillingMapper {

    private final TransactionsMapper transactionsMapper;

    @Inject
    BillingMapper(TransactionsMapper transactionsMapper) {
        this.transactionsMapper = transactionsMapper;
    }

    Billing mapBilling(List<Transactions> taxamoTransactions) {
        List<Transaction> transactions = taxamoTransactions
                .stream()
                .map(transactionsMapper::mapTransaction)
                .collect(Collectors.toList());

        Transactions mostRecentTransaction = getMostRecentTaxamoTransaction(taxamoTransactions);
        return new Billing(
                mostRecentTransaction.getBuyerName(),
                mostRecentTransaction.getBuyerEmail(),
                mostRecentTransaction.getInvoicePlace(),
                mostRecentTransaction.getInvoiceAddress().getPostalCode(),
                mostRecentTransaction.getInvoiceAddress().getCity(),
                mapCountryCode(mostRecentTransaction.getBillingCountryCode()),
                mapCreditCardPrefix(mostRecentTransaction),
                transactionsMapper.mapPlan(mostRecentTransaction),
                transactions
        );
    }

    private String mapCountryCode(String countryCode) {
        Locale locale = new Locale("", countryCode);
        return locale.getDisplayCountry();
    }

    private Transactions getMostRecentTaxamoTransaction(List<Transactions> transactions) {
        return transactions.stream().sorted((t1, t2) -> {
            LocalDate localDate1 = LocalDate.parse(t1.getCreateTimestamp(), DateTimeFormatter.ISO_DATE_TIME);
            LocalDate localDate2 = LocalDate.parse(t2.getCreateTimestamp(), DateTimeFormatter.ISO_DATE_TIME);
            return localDate1.compareTo(localDate2);
        }).findFirst().orElse(transactions.get(0));
    }

    private String mapCreditCardPrefix(Transactions mostRecentTransaction) {
        String creditCardWithWildcards = mostRecentTransaction.getBuyerCreditCardPrefix() + StringUtils.repeat("*", 11);
        return String.format(
                "%s %s %s %s",
                creditCardWithWildcards.substring(0, 4),
                creditCardWithWildcards.substring(4, 8),
                creditCardWithWildcards.substring(8, 12),
                creditCardWithWildcards.substring(12, 16)
        );
    }
}
