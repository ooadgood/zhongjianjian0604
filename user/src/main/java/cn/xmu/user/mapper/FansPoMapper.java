package cn.xmu.user.mapper;

import cn.xmu.user.model.po.FansPo;
import cn.xmu.user.model.po.FansPoExample;
import java.util.List;

public interface FansPoMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table fans
     *
     * @mbg.generated
     */
    int deleteByPrimaryKey(Long id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table fans
     *
     * @mbg.generated
     */
    int insert(FansPo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table fans
     *
     * @mbg.generated
     */
    int insertSelective(FansPo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table fans
     *
     * @mbg.generated
     */
    List<FansPo> selectByExample(FansPoExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table fans
     *
     * @mbg.generated
     */
    FansPo selectByPrimaryKey(Long id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table fans
     *
     * @mbg.generated
     */
    int updateByPrimaryKeySelective(FansPo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table fans
     *
     * @mbg.generated
     */
    int updateByPrimaryKey(FansPo record);
}