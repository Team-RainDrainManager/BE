package com.rainbutler.domain.bugreport.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;

/**
 * 스크린샷 S3 key 목록을 쉼표로 이은 문자열 한 컬럼으로 저장합니다. (key에는 쉼표가 들어가지 않는다)
 *
 * <p>최대 3장이라 별도 테이블 대신 컬럼 하나에 둡니다. 빈 목록은 null로 저장합니다.
 */
@Converter
public class PhotoKeysConverter implements AttributeConverter<List<String>, String> {

    private static final String DELIMITER = ",";

    @Override
    public String convertToDatabaseColumn(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        return String.join(DELIMITER, keys);
    }

    @Override
    public List<String> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(DELIMITER)).map(String::strip).toList();
    }
}
