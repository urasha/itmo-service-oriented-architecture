package ru.itmo.soa.contract;

import java.util.List;

public interface FlatOperations {
    Flat create(FlatInput input);
    Flat get(long id);
    Flat replace(long id, FlatInput input);
    Flat patch(long id, String patch);
    void delete(long id);
    FlatPage list(int page, int size, String filter, List<String> sorts);
    Flat minCreationDate();
    Flat maxNew();
    long count(Transport transport);
}
