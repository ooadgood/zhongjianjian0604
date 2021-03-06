package cn.xmu.article.controller;

import cn.xmu.api.BaseController;
import cn.xmu.api.article.ArticleControllerApi;
import cn.xmu.api.config.RabbitMQConfig;
import cn.xmu.article.service.ArticleService;
import cn.xmu.enums.ArticleCoverType;
import cn.xmu.enums.ArticleReviewStatus;
import cn.xmu.enums.JsonUtils;
import cn.xmu.enums.YesOrNo;
import cn.xmu.exception.GraceException;
import cn.xmu.grace.result.GraceJSONResult;
import cn.xmu.grace.result.ResponseStatusEnum;
import cn.xmu.pojo.Category;
import cn.xmu.pojo.bo.NewArticleBO;
import cn.xmu.pojo.vo.ArticleDetailVO;
import cn.xmu.utils.PagedGridResult;
import com.mongodb.client.gridfs.GridFSBucket;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ArticleController extends BaseController implements ArticleControllerApi {

    final static Logger logger = LoggerFactory.getLogger(ArticleController.class);

    @Autowired
    private ArticleService articleService;

    @Override
    public GraceJSONResult createArticle(@Valid NewArticleBO newArticleBO,
                                         BindingResult result) {

        // ??????BindingResult?????????????????????????????????????????????????????????return
        if (result.hasErrors()) {
            Map<String, String> errorMap = getErrors(result);
            return GraceJSONResult.errorMap(errorMap);
        }

        // ??????????????????????????????????????????????????????????????????
        if (newArticleBO.getArticleType() == ArticleCoverType.ONE_IMAGE.type) {
            if (StringUtils.isBlank(newArticleBO.getArticleCover())) {
                return GraceJSONResult.errorCustom(ResponseStatusEnum.ARTICLE_COVER_NOT_EXIST_ERROR);
            }
        } else if (newArticleBO.getArticleType() == ArticleCoverType.WORDS.type) {
            newArticleBO.setArticleCover("");
        }

        // ????????????id????????????
//        String allCatJson = redis.get(REDIS_ALL_CATEGORY);
//        Category temp = null;
//        if (StringUtils.isBlank(allCatJson)) {
//            return GraceJSONResult.errorCustom(ResponseStatusEnum.SYSTEM_OPERATION_ERROR);
//        } else {
//            List<Category> catList =
//                    JsonUtils.jsonToList(allCatJson, Category.class);
//            for (Category c : catList) {
//                if(c.getId() == newArticleBO.getCategoryId()) {
//                    temp = c;
//                    break;
//                }
//            }
//            if (temp == null) {
//                return GraceJSONResult.errorCustom(ResponseStatusEnum.ARTICLE_CATEGORY_NOT_EXIST_ERROR);
//            }
//        }

//        System.out.println(newArticleBO.toString());

        articleService.createArticle(newArticleBO);

        return GraceJSONResult.ok();
    }

    public LocalDateTime string2LDT(String string){
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime LocalTime = LocalDateTime.parse("2010-06-01 09:42:37",df);
        return LocalTime;

    }
    @Override
    public GraceJSONResult queryMyList(String userId,
                                       String keyword,
                                       Integer status,
                                       LocalDateTime startDate,
                                       LocalDateTime endDate,
                                       Integer page,
                                       Integer pageSize) {

        if (StringUtils.isBlank(userId)) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.ARTICLE_QUERY_PARAMS_ERROR);
        }

        if (page == null) {
            page = COMMON_START_PAGE;
        }
        if (pageSize == null) {
            pageSize = COMMON_PAGE_SIZE;
        }
//
        LocalDateTime startDateTime = LocalDateTime.of(2010, 1, 1, 0, 0,0);
        LocalDateTime endDateTime = LocalDateTime.of(2022, 1, 1, 0, 0,0);


        // ???????????????????????????service
        PagedGridResult grid = articleService.queryMyArticleList(userId,
                keyword,
                status,
                startDateTime,
                endDateTime,
                page,
                pageSize);

        return GraceJSONResult.ok(grid);
    }

    @Override
    public GraceJSONResult queryAllList(Integer status, Integer page, Integer pageSize) {

        if (page == null) {
            page = COMMON_START_PAGE;
        }

        if (pageSize == null) {
            pageSize = COMMON_PAGE_SIZE;
        }

        PagedGridResult gridResult = articleService.queryAllArticleListAdmin(status, page, pageSize);

        return GraceJSONResult.ok(gridResult);
    }

    @Override
    public GraceJSONResult doReview(String articleId, Integer passOrNot) {

        Integer pendingStatus;
        if (passOrNot == YesOrNo.YES.type) {
            // ????????????
            pendingStatus = ArticleReviewStatus.SUCCESS.type;
        } else if (passOrNot == YesOrNo.NO.type) {
            // ????????????
            pendingStatus = ArticleReviewStatus.FAILED.type;
        } else {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.ARTICLE_REVIEW_ERROR);
        }

        // ?????????????????????????????????????????????????????????????????????
        articleService.updateArticleStatus(articleId, pendingStatus);

        if (pendingStatus == ArticleReviewStatus.SUCCESS.type) {
            // ??????????????????????????????????????????html
            try {
//                createArticleHTML(articleId);
                String articleMongoId = createArticleHTMLToGridFS(articleId);

                // ?????????????????????????????????????????????
                articleService.updateArticleToGridFS(articleId, articleMongoId);
//                // ??????????????????????????????html
//                doDownloadArticleHTML(articleId, articleMongoId);

                // ???????????????mq?????????????????????????????????????????????html
                doDownloadArticleHTMLByMQ(articleId, articleMongoId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return GraceJSONResult.ok();
    }

    @Value("${freemarker.html.article}")
    private String articlePath;

    // ????????????HTML
    public void createArticleHTML(String articleId) throws Exception {

        Configuration cfg = new Configuration(Configuration.getVersion());
        String classpath = this.getClass().getResource("/").getPath();
        cfg.setDirectoryForTemplateLoading(new File(classpath + "templates"));

        Template template = cfg.getTemplate("detail.ftl", "utf-8");

        // ???????????????????????????
        ArticleDetailVO detailVO = getArticleDetail(articleId);
        Map<String, Object> map = new HashMap<>();
        map.put("articleDetail", detailVO);

        File tempDic = new File(articlePath);
        if (!tempDic.exists()) {
            tempDic.mkdirs();
        }

        String path = articlePath + File.separator + detailVO.getId() + ".html";

        Writer out = new FileWriter(path);
        template.process(map, out);
        out.close();
    }

    @Autowired
    private GridFSBucket gridFSBucket;

    // ????????????HTML
    public String createArticleHTMLToGridFS(String articleId) throws Exception {

        Configuration cfg = new Configuration(Configuration.getVersion());
        String classpath = this.getClass().getResource("/").getPath();
        cfg.setDirectoryForTemplateLoading(new File(classpath + "templates"));

        Template template = cfg.getTemplate("detail.ftl", "utf-8");

        // ???????????????????????????
        ArticleDetailVO detailVO = getArticleDetail(articleId);
        Map<String, Object> map = new HashMap<>();
        map.put("articleDetail", detailVO);

        String htmlContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, map);
//        System.out.println(htmlContent);

        InputStream inputStream = IOUtils.toInputStream(htmlContent);
        ObjectId fileId = gridFSBucket.uploadFromStream(detailVO.getId() + ".html",inputStream);
        return fileId.toString();
    }

    // ??????????????????rest???????????????????????????
    public ArticleDetailVO getArticleDetail(String articleId) {
        String url
                = "http://localhost:8001/portal/article/detail?articleId=" + articleId;
        ResponseEntity<GraceJSONResult> responseEntity
                = restTemplate.getForEntity(url, GraceJSONResult.class);
        GraceJSONResult bodyResult = responseEntity.getBody();
        ArticleDetailVO detailVO = null;
        if (bodyResult.getStatus() == 200) {
            String detailJson = JsonUtils.objectToJson(bodyResult.getData());
            detailVO = JsonUtils.jsonToPojo(detailJson, ArticleDetailVO.class);
        }
        return detailVO;
    }

    private void doDownloadArticleHTML(String articleId, String articleMongoId) {

        String url =
                "http://localhost:8002/article/html/download?articleId="
                        + articleId +
                        "&articleMongoId="
                        + articleMongoId;
        ResponseEntity<Integer> responseEntity = restTemplate.getForEntity(url, Integer.class);
        int status = responseEntity.getBody();
        if (status != HttpStatus.OK.value()) {
            GraceException.display(ResponseStatusEnum.ARTICLE_REVIEW_ERROR);
        }
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private void doDownloadArticleHTMLByMQ(String articleId, String articleMongoId) {

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_ARTICLE,
                "article.download.do",
                articleId + "," + articleMongoId);
    }

    @Override
    public GraceJSONResult delete(String userId, String articleId) {
        articleService.deleteArticle(userId, articleId);
        return GraceJSONResult.ok();
    }

    @Override
    public GraceJSONResult withdraw(String userId, String articleId) {
        articleService.withdrawArticle(userId, articleId);
        return GraceJSONResult.ok();
    }



}
