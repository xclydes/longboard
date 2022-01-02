package com.xclydes.finance.longboard.wave.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xclydes.finance.longboard.upwork.models.Team;
import com.xclydes.finance.longboard.wave.fragment.CustomerFragment;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TeamToCustomerFragment implements Converter<Team, CustomerFragment> {

    private final ObjectMapper objectMapper;

    public TeamToCustomerFragment(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CustomerFragment convert(@NotNull final Team source) {
        // Convert this the source to a JSON String
        final ObjectNode objectReader = this.objectMapper.createObjectNode();
        // Store the input for future reference
        objectReader.putPOJO("upwork", source);
        // Generate the Wave format
        return new CustomerFragment(
                "Customer",
                "",
                source.reference,
                source.name,
                "",
                source.company_name,
                objectReader.toPrettyString()
        );
    }
}
