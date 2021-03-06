package cn.xmu.files.controller;

import cn.xmu.api.BaseController;
import cn.xmu.api.files.FileUploaderControllerApi;
import cn.xmu.exception.GraceException;
import cn.xmu.files.mapper.AppUserPoMapper;
import cn.xmu.files.model.po.AppUserPo;
import cn.xmu.files.service.UploaderService;
import cn.xmu.files.service.impl.UploaderServiceImpl;
import cn.xmu.files.util.ImgUploads;
import cn.xmu.grace.result.GraceJSONResult;
import cn.xmu.grace.result.ResponseStatusEnum;
import cn.xmu.pojo.bo.NewAdminBO;
import cn.xmu.utils.FileUtils;
import cn.xmu.utils.extend.AliImageReviewUtils;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mysql.cj.util.Base64Decoder;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import com.mongodb.client.model.Filters;

@RestController
public class FileUploaderController  implements FileUploaderControllerApi {

    final static Logger logger = LoggerFactory.getLogger(FileUploaderController.class);
    @Autowired
    private AliImageReviewUtils aliImageReviewUtils;

    @Autowired
    private UploaderService uploaderService;

    @Autowired
    private AppUserPoMapper appUserPoMapper;

    @Autowired
    private GridFSBucket gridFSBucket;

    @Value("${minio.endpoint}")
    private String ENDPOINT;
    @Value("${minio.bucketName}")
    private String BUCKETNAME;
    @Value("${minio.accessKey}")
    private String ACCESSKEY;
    @Value("${minio.secretKey}")
    private String SECRETKEY;

    @PostMapping("/uploadFace")
    @Override
    public GraceJSONResult uploadFace(Long userId,
                                      MultipartFile file)throws Exception  {

        AppUserPo appUserPo = appUserPoMapper.selectByPrimaryKey(userId);

        if(appUserPo == null){

            return GraceJSONResult.errorCustom(ResponseStatusEnum.USER_NOT_EXIST_ERROR);
        }

        String path = null;
        try {

            path = ImgUploads.remoteSaveImg(file, 1, ACCESSKEY, SECRETKEY, BUCKETNAME, ENDPOINT);

            //??????????????????
            if (path=="511") {
                logger.debug("511");
                return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_UPLOAD_FAILD);
            }


            // ???????????????????????????
            if (path=="512") {
                logger.debug("512");
                return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_FORMATTER_FAILD);
            }
            //?????????500kb??????????????????????????????
            if (path=="513") {
                logger.debug("513");
                return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_MAX_SIZE_ERROR);
            }

            String oldFilename = appUserPo.getFace();

            appUserPo.setFace(path);
            int ret = appUserPoMapper.updateByPrimaryKeySelective(appUserPo);
            if(ret==0){

                //???????????????????????????????????????????????????
                logger.debug("511");
                ImgUploads.deleteRemoteImg(path, ACCESSKEY, SECRETKEY, BUCKETNAME, ENDPOINT);
                return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_UPLOAD_FAILD);
            }

            //???????????????????????????????????????????????????????????????
            if (oldFilename != null) {

                ImgUploads.deleteRemoteImg(oldFilename, ACCESSKEY, SECRETKEY, BUCKETNAME, ENDPOINT);
            }
        }catch (IOException e) {
            logger.debug("513");
            return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_UPLOAD_FAILD);
        }

        return GraceJSONResult.ok(doAliImageReview(path));
        // return GraceJSONResult.ok(path);


    }


    @Override
    public GraceJSONResult uploadSomeFiles(Long userId,
                                           MultipartFile[] files) throws Exception {

        // ??????list????????????????????????????????????????????????????????????

        AppUserPo appUserPo = appUserPoMapper.selectByPrimaryKey(userId);

        if(appUserPo == null){

            return GraceJSONResult.errorCustom(ResponseStatusEnum.USER_NOT_EXIST_ERROR);
        }

        //???????????????????????????????????????????????????
        List<String> paths = new ArrayList<>();

        if(files!=null&&files.length > 0) {

            for (MultipartFile multipartFile : files) {

                String path = " ";

                path = ImgUploads.remoteSaveImg(multipartFile, 1, ACCESSKEY, SECRETKEY, BUCKETNAME, ENDPOINT);

                //??????????????????
                if (path == "511") {
                    logger.debug("511");
                    continue;
                    //return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_UPLOAD_FAILD);
                }
                if (path == "512") {
                    logger.debug("512");
                    continue;
                    //return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_FORMATTER_FAILD);
                }
                if (path == "513") {
                    logger.debug("513");
                    continue;
                    // return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_MAX_SIZE_ERROR);
                }
                paths.add(path);
            }
        }
        return GraceJSONResult.ok(paths);
    }




    //?????????????????????????????????????????????????????????????????????3000???????????????
    //????????????????????????????????????????????????minio??????????????????minio???error.jpeg???????????????????????????????????????
    public static final String FAILED_IMAGE_URL = "http://znbwc.cn:8000/picture/error.jpeg";
    private String doAliImageReview(String pendingImageUrl) {

        // pendingImageUrl = "http://znbwc.cn:8000/picture/CpoxxFw_8_qAIlFXAAAcIhVPdSg994.png";
        boolean result = false;
        try {
            result = aliImageReviewUtils.reviewImage(pendingImageUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!result) {
            return FAILED_IMAGE_URL;
        }

        return pendingImageUrl;
    }

    @Override
    public GraceJSONResult uploadToGridFS(NewAdminBO newAdminBO) throws Exception {

        String file64 = newAdminBO.getImg64();

        byte[] bytes = Base64.getDecoder().decode(file64.trim());
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

        ObjectId fileID = gridFSBucket.uploadFromStream(newAdminBO.getUsername() + ".png", byteArrayInputStream);

        //???????????????gridFS????????????
        String fileIdStr = fileID.toString();
        return GraceJSONResult.ok(fileIdStr);
    }

    @Override
    public void readInGridFS(String faceId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (StringUtils.isBlank(faceId) || faceId.equalsIgnoreCase("null")) {
            GraceException.display(ResponseStatusEnum.FILE_NOT_EXIST_ERROR);
        }
        //???gridFs?????????
        File adminFace=readGridFSByFaceId(faceId);
        FileUtils.downloadFileByStream(response,adminFace);

    }



    private File readGridFSByFaceId(String faceId) throws Exception {
        GridFSFindIterable gridFSFiles = gridFSBucket.find(Filters.eq("_id", new ObjectId(faceId)));
        GridFSFile gridFS=gridFSFiles.first();

        if (gridFS == null) {

            GraceException.display(ResponseStatusEnum.FILE_NOT_EXIST_ERROR);
        }

        String fileName=gridFS.getFilename();
        System.out.println(fileName);

        File fileTemp=new File("/workspace/temp_face");
        if(!fileTemp.exists()){
            fileTemp.mkdirs();
        }
        File myFile=new File("/workspace/temp_face"+fileName);

        OutputStream os= new FileOutputStream(myFile);
        //??????????????????????????????

        gridFSBucket.downloadToStream(new ObjectId(faceId),os);
        return myFile;

    }

    @Override
    public GraceJSONResult readFace64InGridFS(String faceId, HttpServletRequest request, HttpServletResponse response) throws Exception {

//    ??????gridfs???????????????
        File myface=readGridFSByFaceId(faceId);
//      ???????????????base64
       String base64Face= FileUtils.fileToBase64(myface);

       return GraceJSONResult.ok(base64Face);
    }
}


