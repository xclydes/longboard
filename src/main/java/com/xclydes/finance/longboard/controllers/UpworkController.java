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
import org.springframework.scheduling.SchedulingTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
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
                                               @Argument("companyRef") final String ref) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .companyTeams(token, ref)));
    }

    @QueryMapping
    public Flux<DiaryRecord> upworkCompanyDiary(final Token token,
                                                @Argument("companyOrTeamID") final String id,
                                                @Argument("from") final String from,
                                                @Argument("to") final String to
    ) {
        return Flux.create((sink) -> {
            final LocalDate fromDate = LocalDate.parse(from, DatesUtil.formatterSQL());
            final LocalDate toDate = StringUtils.hasText(to) ?
                    LocalDate.parse(to, DatesUtil.formatterSQL()) : fromDate;
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
                        }
                    });
            // That's all
            sink.complete();
        });
    }

    @QueryMapping
    public Mono<List<User>> upworkTeamUsers(final Token token, @Argument final String ref) {
        return wrapLogic(sink -> sink.success(getUpworkSvc()
                .usersInTeam(token, ref)));
    }

    @QueryMapping
    public Mono<List<FinanceRecord>> upworkUserEarnings(final Token token,
                                                     @Argument final String from,
                                                     @Argument final String to,
                                                     @Argument final String ref
    ) {
        return wrapLogic(sink -> {
            final List<FinanceRecord> earnings = getUpworkSvc()
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
                                                    @Argument("companyId") final String company
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
    public Mono<List<TimeRecord>> upworkUserTime(final Token token,
                                                 @Argument final String from,
                                                 @Argument final String to
    ) {
        return wrapLogic(sink -> {
            final List<TimeRecord> earnings = getUpworkSvc()
                    .timeByUser(
                            token,
                            LocalDate.parse(from, DatesUtil.formatterSQL()),
                            LocalDate.parse(to, DatesUtil.formatterSQL())
                    );
            sink.success(earnings);
        });
    }

    @QueryMapping
    public Mono<List<TimeRecord>> upworkTeamTime(final Token token,
                                                 @Argument final String from,
                                                 @Argument final String to,
                                                 @Argument("companyId") final String company,
                                                 @Argument("teamId") final String team
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
                                                   @Argument("companyId") final String company,
                                                   @Argument("agencyId") final String agency
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
