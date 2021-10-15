package com.xclydes.finance.longboard.controllers;

import com.Upwork.api.OAuthClient;
import com.xclydes.finance.longboard.apis.IClientProvider;
import org.springframework.stereotype.Controller;

@Controller
public class UpworkController extends AbsAPIController<OAuthClient> {

    public UpworkController(final IClientProvider<OAuthClient> clientProvider) {
        super(clientProvider);
    }

}
