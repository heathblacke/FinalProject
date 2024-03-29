package by.javacourse.hotel.controller.command.impl.client;

import by.javacourse.hotel.controller.command.Command;
import by.javacourse.hotel.controller.command.CommandResult;
import by.javacourse.hotel.controller.command.PagePath;
import by.javacourse.hotel.entity.Review;
import by.javacourse.hotel.entity.RoomOrder;
import by.javacourse.hotel.exception.CommandException;
import by.javacourse.hotel.exception.ServiceException;
import by.javacourse.hotel.model.service.ReviewService;
import by.javacourse.hotel.model.service.RoomOrderService;
import by.javacourse.hotel.model.service.ServiceProvider;
import by.javacourse.hotel.util.CurrentPageExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import static by.javacourse.hotel.controller.command.CommandResult.SendingType.FORWARD;
import static by.javacourse.hotel.controller.command.RequestAttribute.*;
import static by.javacourse.hotel.controller.command.RequestParameter.LAST;
import static by.javacourse.hotel.controller.command.SessionAttribute.*;

public class FindOrderByUserIdLastCommand implements Command {
    static Logger logger = LogManager.getLogger();

    @Override
    public CommandResult execute(HttpServletRequest request) throws CommandException {
        HttpSession session = request.getSession();

        Map<String, String> searchParameters = new HashMap<>();
        searchParameters.put(LAST_ATR, request.getParameter(LAST));
        long userId = (Long) session.getAttribute(CURRENT_USER_ID);

        ServiceProvider provider = ServiceProvider.getInstance();
        RoomOrderService roomOrderService = provider.getRoomOrderService();
        ReviewService reviewService = provider.getReviewService();

        CommandResult commandResult = null;
        try {
            List<RoomOrder> orders = roomOrderService.findOrderByUserIdLast(userId, searchParameters);
            if (!orders.isEmpty()) {
                LocalDate theEarliestOrderDate = orders.get(orders.size() - 1).getDate();
                List<Review> reviews = reviewService.findReviewsByUserIdFromDate(userId, theEarliestOrderDate);
                Map<Long, Boolean> reviewsMap = createReviewsMap(orders, reviews);
                Map<Long, Boolean> canBeCanceledMap = roomOrderService.createСanBeCanceledMap(orders);
                request.setAttribute(REVIEW_MAP_ATR, reviewsMap);
                request.setAttribute(CAN_BE_CANCELED_MAP_ATR, canBeCanceledMap);
            }
            request.setAttribute(ORDER_LIST_ATR, orders);
            request.setAttribute(SEARCH_PARAMETER_ATR, searchParameters);
            session.setAttribute(CURRENT_PAGE, CurrentPageExtractor.extract(request));
            commandResult = new CommandResult(PagePath.CLIENT_ORDERS_PAGE, FORWARD);
        } catch (ServiceException e) {
            logger.error("Try to execute FindOrderByUserIdLastCommand was failed " + e);
             throw new CommandException("Try to execute FindOrderByUserIdLastCommand was failed ", e);
        }
        return commandResult;
    }

    private Map<Long, Boolean> createReviewsMap(List<RoomOrder> orders, List<Review> reviews) {
        Map<Long, Boolean> reviewsMap = reviews.stream()
                .collect(Collectors.toMap(r -> Long.valueOf(r.getEntityId()), r -> true));
        orders.stream()
                .filter(o -> o.getStatus() == RoomOrder.Status.COMPLETED && reviewsMap.get(o.getEntityId()) == null)
                .forEach(o -> reviewsMap.put(o.getEntityId(), false));
        return reviewsMap;
    }

}
