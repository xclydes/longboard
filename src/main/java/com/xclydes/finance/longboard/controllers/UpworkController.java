package com.xclydes.finance.longboard.controllers;

import com.Upwork.api.OAuthClient;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.upwork.UpworkSvc;
import com.xclydes.finance.longboard.upwork.models.*;
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
    public Mono<User> upworkUser(final Token token, @Argument String ref) {
        return wrapLogic(sink -> getUpworkSvc()
            .user(token, ref)
            .ifPresentOrElse(
                sink::success,
                () -> sink.error(new ResponseStatusException(HttpStatus.NOT_FOUND))
            ));
    }

    @QueryMapping
    public Mono<List<Company>> upworkCompanies(final Token token,
                                               @Argument String ref) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                        .companies(token, ref)));
    }

    @QueryMapping
    public Mono<List<Team>> upworkTeams(final Token token) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .teams(token)));
    }

    @QueryMapping
    public Mono<List<Team>> upworkCompanyTeams(final Token token,
                                               @Argument("companyOrTeamID") final String id ) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .companyTeams(token, id)));
    }

    @QueryMapping
    public Mono<List<Object>> upworkCompanyDiary(final Token token,
                                        @Argument("companyRef") final String companyRef,
                                        @Argument("date") final String date) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .workDiaryForCompany(token, LocalDate.parse(date, DatesUtil.formatterSQL()), companyRef)));
    }

    @QueryMapping
    public Mono<List<User>> upworkTeamUsers(final Token token, @Argument final String ref) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .usersInTeam(token, ref)));
    }

    @QueryMapping
    public Mono<List<TimeRecord>> upworkUserEarnings(final Token token,
                                                     @Argument final String from,
                                                     @Argument final String to,
                                                     @Argument final String ref
                                          ) {
        return wrapLogic(sink -> {
            final List<TimeRecord> earnings = getUpworkSvc()
                    .earningsForUser(
                            token,
                            LocalDate.parse(from, DatesUtil.formatterSQL()),
                            LocalDate.parse(to, DatesUtil.formatterSQL()),
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<TimeRecord>> upworkCompanyTime(final Token token,
                                                     @Argument final String from,
                                                     @Argument final String to,
                                                     @Argument("companyRef") final String company
                                          ) {
        return wrapLogic(sink -> {
            final List<TimeRecord> earnings = getUpworkSvc()
                    .timeByCompany(
                            token,
                            LocalDate.parse(from, DatesUtil.formatterSQL()),
                            LocalDate.parse(to, DatesUtil.formatterSQL()),
                            company
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<TimeRecord>> upworkTeamTime(final Token token,
                                                     @Argument final String from,
                                                     @Argument final String to,
                                                     @Argument("companyRef") final String company,
                                                     @Argument("teamRef") final String team
                                          ) {
        return wrapLogic(sink -> {
            final List<TimeRecord> earnings = getUpworkSvc()
                    .timeByTeam(
                            token,
                            LocalDate.parse(from, DatesUtil.formatterSQL()),
                            LocalDate.parse(to, DatesUtil.formatterSQL()),
                            company,
                            team
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<TimeRecord>> upworkAgencyTime(final Token token,
                                                     @Argument final String from,
                                                     @Argument final String to,
                                                     @Argument("companyRef") final String company,
                                                     @Argument("agencyRef") final String agency
                                          ) {
        return wrapLogic(sink -> {
            final List<TimeRecord> earnings = getUpworkSvc()
                    .timeByAgency(
                            token,
                            LocalDate.parse(from, DatesUtil.formatterSQL()),
                            LocalDate.parse(to, DatesUtil.formatterSQL()),
                            company,
                            agency
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkEntityAccounting(final Token token,
                                                 @Argument final String ref,
                                                 @Argument final String from,
                                                 @Argument final String to
                                          ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
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
    public Mono<List<FinanceRecord>> upworkUserAccounting(final Token token,
                                                 @Argument final String ref,
                                                 @Argument final String from,
                                                 @Argument final String to
                                          ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
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
