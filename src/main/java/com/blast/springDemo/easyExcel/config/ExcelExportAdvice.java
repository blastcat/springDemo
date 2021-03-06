package com.blast.springDemo.easyExcel.config;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.blast.springDemo.easyExcel.annotation.PoiSupport;
import com.blast.springDemo.core.exception.ServiceException;
import com.blast.springDemo.core.vo.ApiResult;
import com.blast.springDemo.core.vo.ResultUtil;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;

@Log4j2
@ControllerAdvice
public class ExcelExportAdvice implements ResponseBodyAdvice<ApiResult> {

    private final String poiHeaderKey = "__poi__";

    public enum ExportType {
        Excel, Word, Image, PDF
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        PoiSupport poiSupport = methodParameter.getMethodAnnotation(PoiSupport.class);
        return Objects.nonNull(poiSupport);
    }

    @SneakyThrows
    @Override
    public ApiResult<?> beforeBodyWrite(ApiResult apiResult, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        PoiSupport poiSupport = methodParameter.getMethodAnnotation(PoiSupport.class);
        assert poiSupport != null;
        List<String> headers = serverHttpRequest.getHeaders().getOrDefault(poiHeaderKey, new ArrayList<>());
        //?????????????????????????????????????????????
        assert apiResult != null;
        if (!apiResult.isSuccess()) return apiResult;
        //??????????????????????????????POI
        if (!poiSupport.force() && headers.isEmpty()) return apiResult;
        ResultUtil resultUtil = new ResultUtil(apiResult);
        if (poiSupport.type() == ExportType.Excel) {
            if (StringUtils.isEmpty(poiSupport.key()) && resultUtil.isArray()) { //???????????????
                this.repeatedWrite(poiSupport, resultUtil.getList(), serverHttpResponse);
            } else {
                throw new ServiceException("??????????????????????????????????????????????????????????????????");
            }
        }
        return null;
    }

    /**
     * ??????????????????
     */
    public void repeatedWrite(PoiSupport poiSupport, Collection<?> collection, ServerHttpResponse response) throws IOException {
        // ??????1 ?????????????????????sheet
        String fileName = StrUtil.isBlank(poiSupport.value()) ? UUID.randomUUID().toString() : poiSupport.value();
        ExcelWriter excelWriter = null;
        try {
            fillDownloadHeader(response.getHeaders(), fileName, poiSupport.suffix());
            // ?????? ????????????????????????class??????
            excelWriter = EasyExcel.write(response.getBody(),poiSupport.entity()).build();
            // ???????????? ???????????????sheet??????????????????
            WriteSheet writeSheet = EasyExcel.writerSheet(poiSupport.value()).build();
            log.info("Excel????????????:{}{}??????{}????????????", fileName, poiSupport.suffix(), collection.size());
            List<?> temp;
            for (int i = 0; i < 5; i++) {
                // ?????????????????????????????? ????????????????????????????????????????????????
                temp = CollectionUtil.toList(collection.toArray());
                excelWriter.write(temp, writeSheet);
                temp.clear();
            }
            log.info("Excel????????????:{}{}????????????", fileName, poiSupport.suffix());
        } catch (Exception e) {
            log.warn(e);
            throw new ServiceException("??????????????????????????????????????????");
        } finally {
            // ???????????????finish ??????????????????
            if (excelWriter != null) {
                excelWriter.finish();
            }
        }
    }

    @SneakyThrows
    public void fillDownloadHeader(HttpHeaders headers, String fileName, String suffix) {
        headers.set(HttpHeaders.ACCEPT_CHARSET, "UTF-8");
        headers.set(HttpHeaders.CONTENT_TYPE, "application/vnd.ms-excel");
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + URLEncoder.encode(fileName + suffix, "UTF-8"));
    }

}
