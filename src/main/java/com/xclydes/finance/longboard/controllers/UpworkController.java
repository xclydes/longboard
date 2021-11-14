package com.xclydes.finance.longboard.controllers;

import com.Upwork.api.OAuthClient;
import com.xclydes.finance.longboard.apis.IClientProvider;
import com.xclydes.finance.longboard.models.DataPage;
import com.xclydes.finance.longboard.models.Token;
import com.xclydes.finance.longboard.upwork.UpworkSvc;
import com.xclydes.finance.longboard.upwork.models.*;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.SchedulingTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Controller
public class UpworkController extends AbsAPIController<OAuthClient> {

    private final UpworkSvc upworkSvc;
    private final SchedulingTaskExecutor taskExecutor;

    public UpworkController(final IClientProvider<OAuthClient> clientProvider,
                            final UpworkSvc upworkSvc,
                            final SchedulingTaskExecutor longboardTaskExecutor) {
        super(clientProvider);
        this.upworkSvc = upworkSvc;
        this.taskExecutor = longboardTaskExecutor;
    }

    protected SchedulingTaskExecutor getTaskExecutor() {
        return taskExecutor;
    }

    public UpworkSvc getUpworkSvc() {
        return upworkSvc;
    }

    @QueryMapping
    public Mono<User> upworkUser(final Token token, @Argument("ref") String ref) {
        return wrapLogic(sink -> getUpworkSvc()
                .user(token, ref)
                .ifPresentOrElse(
                        sink::success,
                        () -> sink.error(new ResponseStatusException(HttpStatus.NOT_FOUND))
                ));
    }

    @QueryMapping
    public Mono<List<Company>> upworkCompanies(final Token token,
                                               @Argument("ref") String ref) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .companies(token, ref)));
    }

    @QueryMapping
    public Mono<List<Team>> upworkTeams(final Token token) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .teams(token)));
    }

    @QueryMapping
    public Mono<DataPage<EngagementRecord>> upworkEngagements(final Token token,
                                                              @Argument("from") final LocalDate from,
                                                              @Argument("to") LocalDate to,
                                                              @Argument("page") final Integer pageIn,
                                                              @Argument("pageSize") final Integer pageSizeIn,
                                                              @Argument("status") final String status
                                                              ) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .engagements(token, from, to, pageIn, pageSizeIn, status)));
    }

    @QueryMapping
    public Mono<List<Team>> upworkCompanyTeams(final Token token,
                                               @Argument("companyRef") final String ref) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .companyTeams(token, ref)));
    }

    @QueryMapping
    public Flux<DiaryRecord> upworkCompanyDiary(final Token token,
                                                @Argument("companyOrTeamID") final String id,
                                                @Argument("from") final LocalDate fromDate,
                                                @Argument("to") LocalDate to
    ) {
        return Flux.create((sink) -> {
            final LocalDate toDate = to != null ? to : fromDate;
            // Generate a request for every date in-between
            fromDate.datesUntil(toDate.plusDays(1))
                    .parallel()
                    .map(requestDate -> getTaskExecutor().submit(() ->
                            getUpworkSvc().companyWorkdiary(token, requestDate, id))
                    )
                    .forEach(task -> {
                        try {
                            // Success with each
                            task.get().forEach(sink::next);
                        } catch (Exception e) {
                            // TODO What about this?
                            sink.error(e);
                        }
                    });
            // That's all
            sink.complete();
        });
    }

    @QueryMapping
    public Mono<List<User>> upworkTeamUsers(final Token token, @Argument("teamRef") final String ref) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .usersInTeam(token, ref)));
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkUserEarnings(final Token token,
                                                    @Argument("from") final LocalDate from,
                                                    @Argument("to") final LocalDate to,
                                                     @Argument("userRef") final String ref
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .earningsForUser(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<TimeRecord>> upworkCompanyTime(final Token token,
                                                    @Argument("from") final LocalDate from,
                                                    @Argument("to") final LocalDate to,
                                                    @Argument("companyId") final String company
    ) {
        return wrapLogic(sink -> {
            final List<TimeRecord> earnings = getUpworkSvc()
                    .timeByCompany(
                            token,
                            from,
                            to,
                            company
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<TimeRecord>> upworkUserTime(final Token token,
                                                 @Argument("from") final LocalDate from,
                                                 @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<TimeRecord> earnings = getUpworkSvc()
                    .timeByUser(
                            token,
                            from,
                            to
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<TimeRecord>> upworkTeamTime(final Token token,
                                                 @Argument("from") final LocalDate from,
                                                 @Argument("to") final LocalDate to,
                                                 @Argument("companyId") final String company,
                                                 @Argument("teamId") final String team
    ) {
        return wrapLogic(sink -> {
            final List<TimeRecord> earnings = getUpworkSvc()
                    .timeByTeam(
                            token,
                            from,
                            to,
                            company,
                            team
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<TimeRecord>> upworkAgencyTime(final Token token,
                                                   @Argument("from") final LocalDate from,
                                                   @Argument("to") final LocalDate to,
                                                   @Argument("companyId") final String company,
                                                   @Argument("agencyId") final String agency
    ) {
        return wrapLogic(sink -> {
            final List<TimeRecord> earnings = getUpworkSvc()
                    .timeByAgency(
                            token,
                            from,
                            to,
                            company,
                            agency
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkEntityAccounting(final Token token,
                                                            @Argument("ref") final String ref,
                                                            @Argument("from") final LocalDate from,
                                                            @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .accountsForEntity(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkUserAccounting(final Token token,
                                                          @Argument("ref") final String ref,
                                                          @Argument("from") final LocalDate from,
                                                          @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .accountsForUser(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkUserBillings(final Token token,
                                                          @Argument("userRef") final String ref,
                                                          @Argument("from") final LocalDate from,
                                                          @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .billingsForUser(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkFreelancerCompanyBillings(final Token token,
                                                          @Argument("companyRef") final String ref,
                                                          @Argument("from") final LocalDate from,
                                                          @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .billingsForFreelancerCompany(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkFreelancerTeamEarnings(final Token token,
                                                          @Argument("teamRef") final String ref,
                                                          @Argument("from") final LocalDate from,
                                                          @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .earningsForFreelancerTeam(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkBuyerTeamEarnings(final Token token,
                                                          @Argument("teamRef") final String ref,
                                                          @Argument("from") final LocalDate from,
                                                          @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .earningsForBuyersTeam(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkBuyerTeamBillings(final Token token,
                                                          @Argument("teamRef") final String ref,
                                                          @Argument("from") final LocalDate from,
                                                          @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .billingsForBuyersTeam(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkBuyerCompanyEarnings(final Token token,
                                                          @Argument("companyRef") final String ref,
                                                          @Argument("from") final LocalDate from,
                                                          @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .earningsForBuyersCompany(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkBuyerCompanyBillings(final Token token,
                                                          @Argument("companyRef") final String ref,
                                                          @Argument("from") final LocalDate from,
                                                          @Argument("to") final LocalDate to
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
                    .billingsForBuyersCompany(
                            token,
                            from,
                            to,
                            ref
                    );
            sink.success(earnings);
        });
    }
}
