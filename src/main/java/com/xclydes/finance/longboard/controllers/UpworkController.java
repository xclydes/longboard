package com.xclydes.finance.longboard.controllers;

import com.Upwork.api.OAuthClient;
import com.Upwork.models.*;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.svc.UpworkSvc;
import com.xclydes.finance.longboard.util.DatesUtil;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Controller
public class UpworkController extends AbsAPIController<OAuthClient> {

    private final UpworkSvc upworkSvc;

    public UpworkController(final IClientProvider<OAuthClient> clientProvider,
                            final UpworkSvc upworkSvc) {
        super(clientProvider);
        this.upworkSvc = upworkSvc;
    }

    public UpworkSvc getUpworkSvc() {
        return upworkSvc;
    }

    @QueryMapping
    public Mono<User> upworkUser(@Argument final Token token) {
        return wrapLogic(sink -> {
            getUpworkSvc().user(token)
                .ifPresentOrElse(
                    sink::success,
                    () -> sink.error(new ResponseStatusException(HttpStatus.NOT_FOUND))
                );
        });
    }

    @QueryMapping
    public Mono<List<Company>> upworkCompanies(@Argument final Token token,
                                               @Argument String ref) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                        .companies(token, ref)));
    }

    @QueryMapping
    public Mono<List<Team>> upworkTeams(@Argument final Token token,
                                        @Argument String ref) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .teams(token, ref)));
    }

    @QueryMapping
    public Mono<List<Earning>> upworkUserEarnings(@Argument final Token token,
                                                  @Argument final String from,
                                                  @Argument final String to
                                          ) {
        return wrapLogic(sink -> {
            final List<Earning> earnings = getUpworkSvc()
                    .earningsForUser(
                            token,
                            LocalDate.parse(from, DatesUtil.formatterSQL()),
                            LocalDate.parse(to, DatesUtil.formatterSQL()),
                            null
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<Accounting>> upworkEntityAccounting(@Argument final Token token,
                                                 @Argument final String ref,
                                                 @Argument final String from,
                                                 @Argument final String to
                                          ) {
        return wrapLogic(sink -> {
            final List<Accounting> earnings = getUpworkSvc()
                    .accountsForEntity(
                            token,
                            LocalDate.parse(from, DatesUtil.formatterSQL()),
                            LocalDate.parse(to, DatesUtil.formatterSQL()),
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<Accounting>> upworkUserAccounting(@Argument final Token token,
                                                 @Argument final String ref,
                                                 @Argument final String from,
                                                 @Argument final String to
                                          ) {
        return wrapLogic(sink -> {
            final List<Accounting> earnings = getUpworkSvc()
                    .accountsForUser(
                            token,
                            LocalDate.parse(from, DatesUtil.formatterSQL()),
                            LocalDate.parse(to, DatesUtil.formatterSQL()),
                            ref
                    );
            sink.success(earnings);
        });
    }
}
