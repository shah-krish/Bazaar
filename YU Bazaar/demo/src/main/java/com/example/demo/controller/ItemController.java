package com.example.demo.controller;
import java.util.Arrays;
import java.util.List;
import com.example.demo.Email.EmailSender;
import com.example.demo.Email.EmailTemplate;
import com.example.demo.model.Item;
import com.example.demo.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class ItemController {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EmailSender emailSender; // Added EmailSender
    
    @GetMapping("/home")
    public String viewHomePage(Model model) {
        model.addAttribute("items", itemRepository.findAll());
        return "home_page";
    }

    @GetMapping("/product/{id}")
    public String viewProductDetails(@PathVariable Long id, Model model) {
        // Retrieve the item by ID
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid item ID: " + id));

        // Add the item to the model for Thymeleaf
        model.addAttribute("item", item);

        // Return the product details page
        return "product_page"; 
    
    }
    @PostMapping("/add-item")
    public String addItem(@RequestParam String title,
                          @RequestParam double price,
                          @RequestParam String wear,
                          @RequestParam String location,
                          @RequestParam String description,
                          @RequestParam String sellerEmail,
                          @RequestParam("image") MultipartFile imageFile, // File upload
                          Model model) {
        try {
        // Validate wear options
        List<String> validWearOptions = Arrays.asList("new", "used (like new)", "used", "poor");
        if (!validWearOptions.contains(wear)) {
            model.addAttribute("error", "Invalid wear condition selected.");
            return "home_page";
        }

        // Validate location
        List<String> validLocations = Arrays.asList(
                "Accolade Building East", "Accolade Building West", "Archives of Ontario", "Atkinson",
                "Norman Bethune College", "Bennett Centre for Student Services", "Bergeron Centre for Engineering Excellence",
                "Behavioural Sciences Building", "Burton Auditorium", "Chemistry Building", "Calumet College",
                "The Joan & Martin Goldfarb Centre for Fine Arts", "Centre for Film and Theatre", "Curtis Lecture Halls",
                "Computer Methods Building", "Central Square", "Central Utilities Building", "Dahdaleh Building",
                "Executive Learning Centre", "Founders College", "Frost Library (Glendon campus)",
                "Farquharson Life Sciences", "Founders Tennis Court", "Glendon Hall (Glendon campus)",
                "Lorna R. Marsden Honours Court & Welcome Centre", "Hart House (Osgoode Hall Law School)",
                "Health, Nursing and Environmental Studies Building", "Hilliard Residence (Glendon campus)",
                "Ignat Kaneff Building", "Kinsmen Building", "Kaneff Tower", "Lassonde Building", "LA&PS @ IBM Markham",
                "Life Sciences Building", "Lumbers Building", "Rob and Cheryl McEwen Graduate Study & Research Building",
                "McLaughlin College", "Off Campus", "Physical Resources Building",
                "Petrie Science and Engineering Building / Petrie Observatory", "Ross Building - North wing",
                "Ross Building - South wing", "Seneca @ York", "Stong College", "Scott Library",
                "Sherman Health Science Research Centre", "Stedman Lecture Halls", "Seymour Schulich Building",
                "Sheridan College - Trafalgar Campus", "Student Centre", "Steacie Science and Engineering Library",
                "Tennis Canada", "Technology and Enhanced Learning Building", "Track and Field Centre",
                "Tait McKenzie Centre", "Tait Tennis Courts", "Vanier College", "Vari Hall", "Winters College",
                "West Office Building", "William Small Centre", "York Hall (Glendon campus)", "York Lanes"
        );


        if (!validLocations.contains(location)) {
            model.addAttribute("error", "Invalid location selected.");
            return "home_page";
        }
            if (imageFile.isEmpty()) {
                model.addAttribute("error", "Image upload failed. Please select a valid file.");
                return "home_page";
            }

            // Ensure upload directory exists
            String uploadDir = "uploads/";
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String uniqueFileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
            String imagePath = "uploads/" + uniqueFileName;
            Path filePath = uploadPath.resolve(uniqueFileName);
            Files.copy(imageFile.getInputStream(), filePath);

            // Save item to the database
            Item item = new Item();
            item.setTitle(title);
            item.setPrice(price);
            item.setWear(wear);
            item.setLocation(location);
            item.setDescription(description);
            item.setSellerEmail(sellerEmail);
            item.setImagePath(uniqueFileName);
            itemRepository.save(item);

            // Send confirmation email
            EmailTemplate template = EmailTemplate.LISTING_CONFIRMATION;
            String subject = template.getSubject();
            String body = template.getBody(title);
            emailSender.sendEmail(sellerEmail, subject, body);

            model.addAttribute("success", "Item added successfully!");
            return "redirect:/home";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to add item: " + e.getMessage());
            e.printStackTrace(); // Log the error for debugging
            return "home_page";
        }
    }
    @PostMapping("/send-inquiry")
    public String sendInquiry(@RequestParam Long itemId,
                              @RequestParam String buyerName,
                              @RequestParam String buyerEmail,
                              @RequestParam String message,
                              Model model) {
    	
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid item ID"));
        
        String sellerEmail = item.getSellerEmail();
        
        String subject = "New Inquiry for Your Listing on YU Bazaar";
        String emailBody = String.format(
                "Hi,\n\nYou have received a new inquiry for your listing titled '%s'.\n\n" +
                        "Inquiry Details:\n" +
                        "Buyer Name: %s\n" +
                        "Buyer Email: %s\n" +
                        "Message: %s\n\n" +
                        "You can contact the buyer directly to follow up.\n\n" +
                        "Regards,\nYU Bazaar Team",
                item.getTitle(), buyerName, buyerEmail, message
        );
        emailSender.sendEmail(sellerEmail, subject, emailBody);
        model.addAttribute("success", "Inquiry sent successfully to the seller.");
        model.addAttribute("item", item); // Ensure item details are passed back to the product page
    	
      return "product_page";
    }

    @PostMapping("/delete-item")
    public String deleteItem(@RequestParam Long id) {
        itemRepository.deleteById(id);
        return "redirect:/home";
    }

    @GetMapping("/search")
    public String searchItems(@RequestParam String keyword, Model model) {
        List<Item> searchResults = itemRepository.searchItems(keyword);
        model.addAttribute("items", searchResults);
        model.addAttribute("searchKeyword", keyword); // Pass the keyword back for display
        return "home_page";
    }


    @GetMapping("/search-suggestions")
    @ResponseBody
    public List<String> getSearchSuggestions(@RequestParam String keyword) {
        return itemRepository.searchItems(keyword)
                .stream()
                .map(Item::getTitle) // Return only the titles as suggestions
                .toList();
    }


}

