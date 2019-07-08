package sifca.shift.repositories;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import sifca.shift.models.Courier;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CourierExtractor implements ResultSetExtractor<List<Courier>>{
    @Override
    public List<Courier> extractData(ResultSet rs) throws SQLException, DataAccessException{
        Integer count = -1;
        List<Courier> couriers = new ArrayList<>();

        while (rs.next()){
            Courier courier = new Courier();
            courier.setID(Integer.parseInt(rs.getString("Id")));
            courier.setCourierPhone(rs.getString("courierPhone"));
            courier.setStatus(rs.getString("status").charAt(0));
        }
        return new ArrayList<>(couriers);
    }
}