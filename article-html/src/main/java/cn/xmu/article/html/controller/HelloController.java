package cn.xmu.article.html.controller;

import cn.xmu.api.user.HelloControllerApi;
import cn.xmu.grace.result.GraceJSONResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController implements HelloControllerApi {

    final static Logger logger = LoggerFactory.getLogger(cn.xmu.article.html.controller.HelloController.class);

    public Object hello() {
        return GraceJSONResult.ok();
    }

}
