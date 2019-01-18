package controllers;
import controllers.Auth.Secured;
import models.Book;
import models.Tag;
import models.User;
import play.data.DynamicForm;
import play.data.Form;
import play.data.FormFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by azeem on 4/30/2017.
 */

import play.mvc.Security;
import views.html.books.*;
import views.html.errors.*;


import javax.inject.Inject;

public class BooksController  extends Controller{

    @Inject
    FormFactory formFactory;
    private static String workingDir = System.getProperty("user.dir");
    private static final String HUMAN_MASTER_FILE = workingDir +"\\PreTestLikert\\Human\\HumanMaster.csv";

    // for all books

    public Result index(){
        List<Book> books = Book.find.all();
        return ok(index.render(books,"All Books"));
    }

    public Result show(Integer id){
        Book book = Book.find.byId(id);
        if(book==null){
            return notFound(_404.render());
        }
        return ok(show.render(book));
    }

    @Security.Authenticated(Secured.class)
    public Result create(){
        List<User> authors = User.find.all();
        Form<Book> bookForm = formFactory.form(Book.class);
        List<Tag> tags = Tag.find.all();
        return ok(create.render(bookForm,authors,tags));
    }

    @Security.Authenticated(Secured.class)
    public Result save() {

        Form<Book> bookForm = formFactory.form(Book.class).bindFromRequest();
        List<User> authors = User.find.all();
        List<Tag> tags = Tag.find.all();

        if(bookForm.hasErrors()){
            flash("danger","Input validation failed.");
            return badRequest(create.render(bookForm,authors,tags));
        }

        // handle book cover upload

        Http.MultipartFormData<File> body = request().body().asMultipartFormData();

        Http.MultipartFormData.FilePart<File> picture = body.getFile("cover");
        Http.MultipartFormData.FilePart<File> pdf = body.getFile("pdf");


        if (picture == null || pdf == null) {
            flash("danger", "Book Cover or pdf is missing.");
            return badRequest(create.render(bookForm,authors,tags));
        }

        String contentType = picture.getContentType();
        String pdfContentType = pdf.getContentType();

        if((!contentType.contains("image")) || (!pdfContentType.contains("pdf"))){
            flash("danger", "Image or pdf is required.");
            return badRequest(create.render(bookForm,authors,tags));
        }

        String uploadFileName = this.saveFile(picture);
        String pdfFIle = this.saveFile(pdf);

        if(uploadFileName == null || pdfFIle == null){
            flash("danger", "Unable to upload file to uploads dir, please try again.");
            return badRequest(create.render(bookForm,authors,tags));
        }

        Map<String,String> input = bookForm.rawData();

        String authorEmail = input.get("author_email");

        Book book = bookForm.get();
        book.cover = uploadFileName;
        book.pdf = pdfFIle;
        book.author = User.find.byId(authorEmail);

        book.tags = Book.parseTagsFromRequest(input);

        book.save();

        flash("success","Book Save Successfully");

        return redirect(routes.BooksController.index());
    }

    @Security.Authenticated(Secured.class)
    public Result edit(Integer id){

        Book book = Book.find.byId(id);
        if(book==null){
            return notFound(_404.render());
        }

        List<User> authors = User.find.all();
        List<Tag> tags = Tag.find.all();

        Form<Book> bookForm = formFactory.form(Book.class).fill(book);
        return ok(edit.render(bookForm,authors,tags));
    }

    @Security.Authenticated(Secured.class)
    public Result update(Integer id){

        Form<Book> bookForm = formFactory.form(Book.class).bindFromRequest();
        List<User> authors = User.find.all();


        if (bookForm.hasErrors()){
            List<Tag> tags = Tag.find.all();
            flash("danger","Input Validation failed.");
            return badRequest(edit.render(bookForm,authors,tags));
        }

        Book book = bookForm.get();
        Book oldBook = Book.find.byId(id);

        if(oldBook == null){
            flash("danger","Book Not Found");
            return redirect(routes.BooksController.edit(id));
        }

        Http.MultipartFormData<File> body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart<File> picture = body.getFile("cover");
        String contentType = picture.getContentType();

        if (!contentType.equals("application/octet-stream")) {
            if(!contentType.contains("image")){
                flash("danger", "Image is required.");
                return redirect(routes.BooksController.edit(id));
            }
            String uploadFileName = this.saveFile(picture);
            if(uploadFileName == null){
                flash("danger", "Unable to upload file to uploads dir, please try again.");
                return redirect(routes.BooksController.edit(id));
            }
            new File("public/uploads/"+oldBook.cover).delete();
            oldBook.cover = uploadFileName;
        }

        Map<String,String> input = bookForm.rawData();
        String authorEmail = input.get("author_email");

        oldBook.title = book.title;
        oldBook.author = User.find.byId(authorEmail);
        oldBook.price = book.price;
        oldBook.details = book.details;
        oldBook.tags = Book.parseTagsFromRequest(input);
        oldBook.update();

        flash("success","Book Updated Successfully.");

        return redirect(routes.BooksController.edit(id));
    }

    @Security.Authenticated(Secured.class)
    public Result destroy(Integer id){

        Book book = Book.find.byId(id);
        if(book == null){
            flash("danger","Book Not Found");
            return notFound();
        }

        new File("public/uploads/"+book.cover).delete();

        book.delete();
        flash("success","Book Deleted Successfully");

        return ok();
    }

    private String saveFile(Http.MultipartFormData.FilePart<File> picture){

        File cover = picture.getFile();
        String fileName = picture.getFilename();
        long unixTime = System.currentTimeMillis() / 1000L;
        String uploadFileName = unixTime+"_"+fileName;
        File uploadDir = new File("public/uploads/"+uploadFileName);

        try {
            Files.copy(cover.toPath(), uploadDir.toPath());
        } catch (IOException e) {
            return null;
        }


        return uploadFileName;
    }

    @Security.Authenticated(Secured.class)
    public  Result questions(){
        System.out.println("rendering form");
        return ok(questionaire.render());
    }

    public Result saveAnswers(){
        DynamicForm dynamicForm = formFactory.form().bindFromRequest();

        //Logger.info("Familiarity is: " + dynamicForm.get("familiar"));
        System.out.println("Familiarity: " +dynamicForm.get("familiar"));
        System.out.println("Affraid: " +dynamicForm.get("fear"));
        System.out.println("Useful: " +dynamicForm.get("useful"));
        System.out.println("Anxiety: " +dynamicForm.get("anxious"));

        BufferedWriter bw1 = null;
        FileWriter fw1 = null;

        BufferedWriter bw2 = null;
        FileWriter fw2 = null;

        try {

            File file1 = new File(HUMAN_MASTER_FILE);

            // if file doesnt exists, then create it
            if (!file1.exists()) {
                file1.createNewFile();
            }

            // true = append file
            fw1 = new FileWriter(file1.getAbsoluteFile(), true);
            bw1 = new BufferedWriter(fw1);

            bw1.append(dynamicForm.get("familiar"));
            bw1.append(",");
            bw1.append(dynamicForm.get("fear"));
            bw1.append(",");
            bw1.append(dynamicForm.get("useful"));
            bw1.append(",");
            bw1.append(dynamicForm.get("anxious"));
            bw1.append("\r\n");

        } catch (IOException e) {

            e.printStackTrace();

        } finally {

            try {

                if (bw1 != null)
                    bw1.close();

                if (fw1 != null)
                    fw1.close();

            } catch (IOException ex) {

                ex.printStackTrace();

            }
        }

        try{

            String SUBJECT_HUMAN_FILE = workingDir +"\\PreTestLikert\\Human\\Individual\\";

            File f = new File(workingDir +"\\PreTestLikert\\Human\\Individual");
            File[] files = f.listFiles();

            int fileCount = files.length+1; //zero indexed

            SUBJECT_HUMAN_FILE +=fileCount +"_" +new SimpleDateFormat("yyyyMMddHHmm'.csv'").format(new Date());

            File file2 = new File(SUBJECT_HUMAN_FILE);

            if (!file2.exists()) {
                file2.createNewFile();
            }

            fw2 = new FileWriter(file2.getAbsoluteFile(), true);
            bw2 = new BufferedWriter(fw2);

            bw2.write(dynamicForm.get("familiar"));
            bw2.write(",");
            bw2.write(dynamicForm.get("fear"));
            bw2.write(",");
            bw2.write(dynamicForm.get("useful"));
            bw2.write(",");
            bw2.write(dynamicForm.get("anxious"));
            bw2.write("\r\n");

        }catch(IOException e){
            e.printStackTrace();

        }finally {

            try{
                if (bw2 != null)
                    bw2.close();

                if (fw2 != null)
                    fw2.close();

            }catch (IOException ex){

                ex.printStackTrace();
            }
        }


        System.out.println("rendering form");
        return ok(questionaire.render());
    }



}
