package sifca.shift.repositories;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import sifca.shift.exception.NotFoundException;
import sifca.shift.exception.modelsException.DatabaseException;
import sifca.shift.exception.modelsException.OrderException;
import sifca.shift.models.Courier;
import sifca.shift.models.Order;
import sifca.shift.models.ActiveOrders;
import sifca.shift.models.MyOrders;
import sifca.shift.services.OrderService;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "use.database", havingValue = "true")
public class OrderAndCourierDatabase implements OrderAndCourierRepository{
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    List<Courier> couriers = new ArrayList<>();
    private Integer count = -1;

    @Autowired
    OrderService orderService;

    List<ActiveOrders> orders = new ArrayList<>();

    @Autowired
    private CourierExtractor courierExtractor;

    @Autowired
    private OrderExtractor orderExtractor;

    @Autowired
    private MyOrdersExtractor myOrdersExtractor;

    @Autowired
    private ActiveOrdersExtractor activeOrdersExtractor;

    @PostConstruct
    public void initialize(){
        String createTable = "CREATE TABLE IF NOT EXISTS Couriers(" +
                "OrderId int NOT NULL," +
                "CourierPhone nvarchar(11) NOT NULL," +
                "Status varchar(15) NOT NULL CHECK(Status IN('Done', 'Failed', 'Processing', 'Waiting', 'Closed'))" +
                ");";
        jdbcTemplate.update(createTable, new MapSqlParameterSource());
        create(1, "89135895600", "Processing");
    }

    @Override
    public boolean existAndActive(Integer OrderId){
        if (orderService.exists(OrderId)){
            Order order = orderService.getOrder(OrderId);
            if (order.getStatus().equals("Active")){
                return true;
            }
            return false;
        }
        throw new DatabaseException();
    }

    @Override
    public void create(Integer orderId, String courierPhone, String Status){
        if (existAndActive(orderId) && !courierExists(orderId)){
            String SqlInsert = "INSERT INTO Couriers VALUES(:orderId, :courierPhone,:Status);";
            MapSqlParameterSource param = new MapSqlParameterSource()
                    .addValue("orderId", orderId)
                    .addValue("courierPhone", courierPhone)
                    .addValue("Status", Status);
            jdbcTemplate.update(SqlInsert, param);
        }
    }


    @Override
    public Courier getCourier(Integer id){
        String sql = "SELECT * FROM couriers WHERE OrderId = :OrderId;";
        MapSqlParameterSource param = new MapSqlParameterSource()
                .addValue("OrderId", id);
        List<Courier> couriers = jdbcTemplate.query(sql, param, courierExtractor);
        return couriers.get(0);
    }


    @Override
    public boolean isCustomer(Integer id, String phone){
        if (orderService.exists(id)){
            String sql = "SELECT * FROM orders " +
                    "JOIN Couriers ON orders.OrderId = Couriers.OrderId " +
                    "WHERE orders.OrderId = :OrderId AND orders.orderPhone = :phone;";
            MapSqlParameterSource param = new MapSqlParameterSource()
                    .addValue("OrderId", id)
                    .addValue("phone", phone);
            List<Order> orders = jdbcTemplate.query(sql, param, orderExtractor);
            if (orders.isEmpty())
                return false;
            return true;
        }
        throw new DatabaseException();
    }


    @Override
    public boolean isCourier(Integer id, String phone){
        if (orderService.exists(id)){
            String sql = "SELECT * FROM orders " +
                    "JOIN Couriers ON orders.OrderId = Couriers.OrderId " +
                    "WHERE Orders.OrderId = :OrderId AND couriers.CourierPhone = :phone;";
            MapSqlParameterSource param = new MapSqlParameterSource()
                    .addValue("OrderId", id)
                    .addValue("phone", phone);
            List<Order> orders = jdbcTemplate.query(sql, param, orderExtractor);
            if (orders.isEmpty())
                return false;
            return true;
        }
        throw new DatabaseException();
    }

    @Override
    public boolean courierExists(Integer OrderId){
        String sql = "SELECT * FROM couriers WHERE OrderId = :OrderId;";
        MapSqlParameterSource param = new MapSqlParameterSource()
                .addValue("OrderId", OrderId);
        List<Courier> couriers = jdbcTemplate.query(sql, param, courierExtractor);
        if (couriers.isEmpty())
            return false;
        return true;
    }

    // CHANGE, try to make this more easy and clearer
    @Override
    public void changeStatus(Integer OrderId, String Status, String phone) {
        if ((!isCustomer(OrderId, phone) && !isCourier(OrderId, phone)          // Если это не заказчик, и не курьер или
                || (!Status.equals("Closed") &&
                Status.equals("Done"))))                           // запрос не на отмену или закрытие заказа
            throw new OrderException();                                      // вернуть ошибку
        else {
            if (Status.equals("Closed") && isCustomer(OrderId, phone)) {                  // Если запрос на отмену, и это заказчик
                if (!courierExists(OrderId)) {                                  // Если у заказа есть курьер
                    String sql = "UPDATE couriers SET status = :status" +
                            "WHERE OrderId = :OrderId;";
                    MapSqlParameterSource param = new MapSqlParameterSource()
                            .addValue("OrderId", OrderId)
                            .addValue("status", Status);
                    jdbcTemplate.update(sql, param);
                }
                orderService.changeStatus(OrderId, Status);          // Меняем статус заказа на отмененный
            } else {                                            // Если это курьер
                if (Status.equals("Done")) {                            // Если запрос на закрытие
                    orderService.changeStatus(OrderId, Status);      // Закрытие заказа со стороны заказчика
                } else {                                      // Если запрос на отмену
                    orderService.changeStatus(OrderId, "Active");         // Статус заказа меняется на активный
                    String sql = "UPDATE couriers SET status = :status" +
                            "WHERE OrderId = :OrderId;";
                    MapSqlParameterSource param = new MapSqlParameterSource()
                            .addValue("OrderId", OrderId)
                            .addValue("status", Status);
                    jdbcTemplate.update(sql, param);                // Закрытие заказа со стороны курьера
                }
            }
        }
    }

    @Override
    public List<MyOrders> getMyOrders(String phone){
        List<MyOrders> myOrders = new ArrayList<>();
        // ADDING AS A CUSTOMER
        String sql = "SELECT orderPhone, fromAddress, toAddress, price, orderTime," +
                " deliveryTime, status, 0 as access, note, size FROM Orders " +
                "WHERE orderPhone = :phone;";
        MapSqlParameterSource param = new MapSqlParameterSource()
                .addValue("phone",phone);
        myOrders.addAll(jdbcTemplate.query(sql, param, myOrdersExtractor));
        // ADDING AS A COURIER
        sql = "SELECT orderPhone, fromAddress, toAddress, price, orderTime," +
                " deliveryTime, couriers.status, 0 as access, note, size FROM Orders " +
                "JOIN couriers ON Orders.OrderId = Couriers.OrderId " +
                "WHERE couriers.courierPhone = :phone;";
        param = new MapSqlParameterSource()
                .addValue("phone",phone);
        myOrders.addAll(jdbcTemplate.query(sql, param, myOrdersExtractor));
        return myOrders;
    }

    @Override
    public String getPhone(Integer Id){
        if (courierExists(Id)){
            String Sql = "SELECT CourierPhone FROM couriers" +
                    "WHERE OrderId = :Id";
            MapSqlParameterSource param = new MapSqlParameterSource()
                    .addValue("Id", Id);
            List <Courier> couriers = jdbcTemplate.query(Sql, param, courierExtractor);
            return couriers.get(0).getCourierPhone();
        }
        throw new DatabaseException();
    }

    @Override
    public List<ActiveOrders> getActiveOrders(){
        List<ActiveOrders> activeOrders = new ArrayList<>();
        String sql = "SELECT orderPhone, fromAddress, toAddress, price, orderTime," +
                " deliveryTime, note, size FROM Orders " +
                "WHERE status = 'A';";
        activeOrders.addAll(jdbcTemplate.query(sql, activeOrdersExtractor));
        return activeOrders;
    }

    @Override
    public List<Courier> getAll(){
        String sql = "SELECT * FROM couriers;";
        List<Courier> couriers = jdbcTemplate.query(sql, courierExtractor);
        return couriers;
    }

    @Override
    public String getStatus(Integer orderId, String phone){
        if (isCustomer(orderId, phone)){
            return orderService.getOrder(orderId).getStatus();
        }
        if (isCourier(orderId, phone)){
            return getCourier(orderId).getStatus();
        }
        throw new OrderException();
    }
}