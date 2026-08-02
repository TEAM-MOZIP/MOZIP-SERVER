package com.mozip.server.policy.repository;

import com.mozip.server.policy.exception.InvalidSortFieldException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PolicySortValidator {

    private static final Set<String> ALLOWED_FIELDS = Set.of("createdAt", "applicationEndDate");
    private static final Sort.Order ID_TIE_BREAK = Sort.Order.desc("id");

    private PolicySortValidator() {
    }

    public static Pageable validate(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_FIELDS.contains(order.getProperty())) {
                throw new InvalidSortFieldException(order.getProperty());
            }
            orders.add(order);
        }
        orders.add(ID_TIE_BREAK);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }
}
