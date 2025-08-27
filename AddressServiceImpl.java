package com.wlt.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wlt.mapper.AddressMapper;
import com.wlt.mapper.AreaMapper;
import com.wlt.mapper.CityMapper;
import com.wlt.mapper.ProvinceMapper;
import com.wlt.pojo.Address;
import com.wlt.pojo.Area;
import com.wlt.pojo.City;
import com.wlt.pojo.Province;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@DubboService
public class AddressServiceImpl implements AddressService {
    @Autowired
    private ProvinceMapper provinceMapper;
    @Autowired
    private CityMapper cityMapper;
    @Autowired
    private AreaMapper areaMapper;
    @Autowired
    private AddressMapper addressMapper;
    
    @Override
    public List<Province> findAllProvince () {
        return provinceMapper.selectList(null);
    }
    
    @Override
    public List<City> findCityByProvince (Long provinceId) {
        QueryWrapper<City> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("provinceId", provinceId);
        
        return cityMapper.selectList(queryWrapper);
    }
    
    @Override
    public List<Area> findAreaByCity (Long cityId) {
        QueryWrapper<Area> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("cityId", cityId);
        
        return areaMapper.selectList(queryWrapper);
    }
    
    @Override
    public void add (Address address) {
        addressMapper.insert(address);
    }
    
    @Override
    public void update (Address address) {
        addressMapper.updateById(address);
    }
    
    @Override
    public Address findById (Long id) {
        return addressMapper.selectById(id);
    }
    
    @Override
    public void delete (Long id) {
        addressMapper.deleteById(id);
    }
    
    @Override
    public List<Address> findByUser (Long userId) {
        QueryWrapper<Address> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        
        return addressMapper.selectList(queryWrapper);
    }
}
